package com.crewboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crewboard.CrewApp
import com.crewboard.data.ConnectionState
import com.crewboard.data.ResourceDto
import com.crewboard.data.TaskEntity
import com.crewboard.data.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** Survives rotation; exposes immutable state, accepts events. No Android Context inside. */
class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskUiState> =
        combine(repo.tasks(), refreshing, error) { tasks, r, e -> TaskUiState(tasks, r, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskUiState(refreshing = true))

    val connection: StateFlow<ConnectionState> = repo.connection

    val crew: StateFlow<List<ResourceDto>> = repo.resources
        .map { it.values.sortedWith(compareBy({ r -> r.kind }, { r -> r.name })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeTask(id: Int): Flow<TaskEntity?> = repo.task(id)

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        error.value = repo.refresh().exceptionOrNull()?.let { "Offline: showing saved tasks" }
        refreshing.value = false
    }

    fun refreshCrew() = viewModelScope.launch { repo.refreshCrew() }
    fun setStatus(id: Int, status: String) = viewModelScope.launch { repo.updateStatus(id, status) }
    fun reportIssue(taskId: Int?, message: String) = viewModelScope.launch { repo.reportIssue(taskId, message) }

    companion object {
        val Factory = viewModelFactory { initializer { TaskListViewModel(CrewApp.instance.repository) } }
    }
}
