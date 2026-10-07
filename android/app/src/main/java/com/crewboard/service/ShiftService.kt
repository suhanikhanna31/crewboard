package com.crewboard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.crewboard.CrewApp
import com.crewboard.MainActivity
import com.crewboard.data.SocketEvent
import com.crewboard.data.TaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the WebSocket for the length of a shift, so new tasks
 * arrive even when the screen is off. The UI never touches the socket; it just observes Room.
 */
class ShiftService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val app = CrewApp.instance
        job?.cancel()
        job = scope.launch {
            app.repository.refresh()
            app.repository.socket.events { app.repository.login() }.collect { event ->
                app.repository.onEvent(event)
                if (event is SocketEvent.TaskAssigned && event.task.assignedTo == app.settings.resourceId) {
                    notifyNewTask(event.task)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()   // cancels the collector -> closes the socket
        super.onDestroy()
    }

    private fun ongoing(): Notification =
        NotificationCompat.Builder(this, CrewApp.CHANNEL_SHIFT)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("CrewBoard shift active")
            .setContentText("Listening for new tasks")
            .setOngoing(true)
            .build()

    private fun notifyNewTask(task: TaskDto) {
        val open = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, task.id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CrewApp.CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New task: ${task.title}")
            .setContentText("Zone ${task.zone} · ${task.requiredSkill}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(100 + task.id, n) }
    }

    companion object {
        private const val ONGOING_ID = 1
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, ShiftService::class.java))
        fun stop(context: Context) { context.stopService(Intent(context, ShiftService::class.java)) }
        fun restart(context: Context) { stop(context); start(context) }
    }
}
