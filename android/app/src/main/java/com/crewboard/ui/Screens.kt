package com.crewboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crewboard.CrewApp
import com.crewboard.data.ResourceDto
import com.crewboard.data.TaskEntity
import com.crewboard.service.ShiftService

@Composable
fun StatusChip(status: String) {
    Surface(color = statusColor(status), shape = RoundedCornerShape(50)) {
        Text(
            statusLabel(status), color = Color.Black, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun TaskListScreen(vm: TaskListViewModel, onOpen: (Int) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = CrewApp.instance.settings
    var onShift by remember { mutableStateOf(settings.onShift) }

    // Lifecycle-aware: refresh whenever the screen becomes visible again
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My tasks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text(if (onShift) "On shift" else "Off shift", modifier = Modifier.padding(end = 8.dp))
            Switch(checked = onShift, onCheckedChange = { on ->
                onShift = on
                settings.onShift = on
                if (on) ShiftService.start(context) else ShiftService.stop(context)
            })
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.tasks.isEmpty() && !state.refreshing) {
            Text("No tasks yet. Switch on your shift to receive new ones.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.tasks, key = { it.id }) { TaskCard(it) { onOpen(it.id) } }
        }
    }
}

@Composable
fun TaskCard(task: TaskEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(task.status)
            }
            Text("Zone ${task.zone}  ·  ${task.requiredSkill}  ·  priority ${task.priority}")
        }
    }
}

@Composable
fun TaskDetailScreen(id: Int, vm: TaskListViewModel, onBack: () -> Unit) {
    val task by remember(id) { vm.observeTask(id) }.collectAsStateWithLifecycle(initialValue = null)
    var confirmIssue by remember { mutableStateOf(false) }
    val t = task
    if (t == null) {
        Column(Modifier.padding(16.dp)) { Text("Task not found."); TextButton(onClick = onBack) { Text("Back") } }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(t.title, style = MaterialTheme.typography.headlineMedium)
        StatusChip(t.status)
        Text("Zone ${t.zone}  ·  ${t.requiredSkill}  ·  priority ${t.priority}", style = MaterialTheme.typography.titleMedium)
        if (t.description.isNotBlank()) Text(t.description)
        Spacer(Modifier.weight(1f))
        // Big targets: usable with work gloves
        when (t.status) {
            "assigned" -> BigButton("START TASK") { vm.setStatus(t.id, "in_progress") }
            "in_progress" -> BigButton("COMPLETE") { vm.setStatus(t.id, "done"); onBack() }
        }
        if (t.status == "assigned" || t.status == "in_progress") {
            Button(
                onClick = { confirmIssue = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) { Text("⚠ REPORT ISSUE / STOP", style = MaterialTheme.typography.titleLarge) }
        }
    }
    if (confirmIssue) {
        AlertDialog(
            onDismissRequest = { confirmIssue = false },
            title = { Text("Stop and report?") },
            text = { Text("The supervisor is alerted right away and this task is paused.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.reportIssue(t.id, "Issue reported on '${t.title}'")
                    confirmIssue = false
                    onBack()
                }) { Text("Report now") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmIssue = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(88.dp)) {
        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }

@Composable
fun CrewScreen(vm: TaskListViewModel) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.refreshCrew() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Crew and robots", style = MaterialTheme.typography.headlineMedium)
        if (crew.isEmpty()) Text("Nobody on the board yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(crew, key = { it.id }) { ResourceRow(it) } }
    }
}

@Composable
private fun ResourceRow(r: ResourceDto) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (r.kind == "robot") "🤖 " else "👷 ") + r.name, style = MaterialTheme.typography.titleMedium)
                Text(r.skills + if (r.kind == "robot") "  ·  battery ${r.battery.toInt()}%" else "")
            }
            Surface(
                color = if (r.status == "idle") Color(0xFF4CC38A) else Color(0xFFFFB81C),
                shape = RoundedCornerShape(50),
            ) { Text(r.status, color = Color.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        }
    }
}
