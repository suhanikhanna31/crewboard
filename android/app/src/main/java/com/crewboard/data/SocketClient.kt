package com.crewboard.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ConnectionState { LIVE, RECONNECTING, OFFLINE }

sealed interface SocketEvent {
    data object Opened : SocketEvent
    data class TaskAssigned(val task: TaskDto) : SocketEvent
    data class TaskUpdated(val task: TaskDto) : SocketEvent
    data class WorkerStatus(val resource: ResourceDto) : SocketEvent
    data class Telemetry(val resourceId: Int, val battery: Double) : SocketEvent
    data class Issue(val message: String) : SocketEvent
}

fun backoffMs(failures: Int): Long = minOf(30_000L, 1_000L shl failures.coerceIn(0, 5))

fun parseEvent(text: String, json: Json = ApiFactory.json): SocketEvent? = runCatching {
    val o: JsonObject = json.parseToJsonElement(text).jsonObject
    when (o["type"]?.jsonPrimitive?.content) {
        "task_assigned" -> SocketEvent.TaskAssigned(json.decodeFromJsonElement<TaskDto>(o.getValue("task")))
        "task_created", "task_updated" -> SocketEvent.TaskUpdated(json.decodeFromJsonElement<TaskDto>(o.getValue("task")))
        "worker_status" -> SocketEvent.WorkerStatus(json.decodeFromJsonElement<ResourceDto>(o.getValue("resource")))
        "telemetry" -> SocketEvent.Telemetry(o.getValue("resource_id").jsonPrimitive.int, o.getValue("battery").jsonPrimitive.double)
        "issue" -> SocketEvent.Issue(o["message"]?.jsonPrimitive?.content ?: "")
        else -> null
    }
}.getOrNull()

class SocketClient(private val settings: SettingsStore) {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow(ConnectionState.OFFLINE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private fun connectOnce(): Flow<SocketEvent> = callbackFlow {
        val url = settings.baseUrl.trimEnd('/').replaceFirst("http", "ws") + "/ws?token=${settings.token}"
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnectionState.LIVE
                trySend(SocketEvent.Opened)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseEvent(text)?.let { trySend(it) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close(IOException("socket closed: $code"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })
        awaitClose { socket.close(1000, null) }
    }

    /**
     * Cold flow of server events. Cancelling the collector closes the socket (structured
     * concurrency); any failure triggers exponential-backoff reconnect via retryWhen.
     * [beforeConnect] runs on every attempt (used to refresh the JWT).
     */
    fun events(beforeConnect: suspend () -> Unit): Flow<SocketEvent> = flow {
        var failures = 0
        emitAll(
            flow { beforeConnect(); emitAll(connectOnce()) }
                .onEach { if (it is SocketEvent.Opened) failures = 0 }
                .retryWhen { _, _ ->
                    _state.value = ConnectionState.RECONNECTING
                    delay(backoffMs(failures++))
                    true
                }
        )
    }.onCompletion { _state.value = ConnectionState.OFFLINE }
}
