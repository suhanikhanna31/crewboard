package com.crewboard.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import java.io.IOException

/**
 * Single source of truth: the UI only ever observes Room. Network responses and
 * socket events write into Room; user actions write to Room first (optimistic) and
 * are queued for replay when offline.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val api: () -> CrewApi,
    private val settings: SettingsStore,
    val socket: SocketClient,
    private val scheduleSync: () -> Unit,
) {
    private val _resources = MutableStateFlow<Map<Int, ResourceDto>>(emptyMap())
    val resources: StateFlow<Map<Int, ResourceDto>> = _resources
    val connection: StateFlow<ConnectionState> get() = socket.state

    fun tasks(): Flow<List<TaskEntity>> = dao.observeAll()
    fun task(id: Int): Flow<TaskEntity?> = dao.observe(id)

    suspend fun login() {
        val r = api().login(LoginReq(settings.username, settings.password))
        settings.token = r.accessToken
        settings.resourceId = r.resourceId ?: -1
    }

    private suspend fun <T> authed(block: suspend (CrewApi) -> T): T {
        if (settings.token == null) login()
        return try {
            block(api())
        } catch (e: HttpException) {
            if (e.code() == 401) { login(); block(api()) } else throw e
        }
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        flushPending()
        val rid = settings.resourceId
        val remote = authed { it.tasks(if (rid >= 0) rid else null) }
        dao.replaceAll(remote.map { it.toEntity() })
    }

    suspend fun refreshCrew(): Result<Unit> = runCatching {
        _resources.value = authed { it.resources() }.associateBy { it.id }
    }

    suspend fun updateStatus(id: Int, status: String) {
        dao.setStatus(id, status)                       // optimistic: UI updates instantly
        dao.enqueue(PendingAction(taskId = id, status = status))
        if (!flushPending()) scheduleSync()             // offline -> WorkManager retries later
    }

    suspend fun reportIssue(taskId: Int?, message: String) {
        if (taskId != null) dao.setStatus(taskId, "blocked")
        try {
            authed { it.issue(IssueReq(message, taskId)) }
        } catch (e: IOException) {
            if (taskId != null) { dao.enqueue(PendingAction(taskId = taskId, status = "blocked")); scheduleSync() }
        }
    }

    /** Replays queued actions in order. Returns false if still offline. */
    suspend fun flushPending(): Boolean {
        for (action in dao.pending()) {
            try {
                authed { it.setStatus(action.taskId, StatusReq(action.status)) }
                dao.remove(action)
            } catch (e: HttpException) {
                dao.remove(action)          // server rejected it (e.g. invalid transition): drop
            } catch (e: IOException) {
                return false
            }
        }
        return true
    }

    suspend fun onEvent(event: SocketEvent) {
        val me = settings.resourceId
        when (event) {
            is SocketEvent.TaskAssigned -> if (event.task.assignedTo == me) dao.upsertOne(event.task.toEntity())
            is SocketEvent.TaskUpdated ->
                if (event.task.assignedTo == me) dao.upsertOne(event.task.toEntity()) else dao.delete(event.task.id)
            is SocketEvent.WorkerStatus -> _resources.update { it + (event.resource.id to event.resource) }
            is SocketEvent.Telemetry -> _resources.update { m ->
                m[event.resourceId]?.let { m + (event.resourceId to it.copy(battery = event.battery)) } ?: m
            }
            else -> Unit
        }
    }
}
