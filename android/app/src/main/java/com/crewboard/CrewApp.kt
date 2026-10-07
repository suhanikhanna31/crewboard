package com.crewboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.crewboard.data.ApiFactory
import com.crewboard.data.AppDatabase
import com.crewboard.data.CrewApi
import com.crewboard.data.SettingsStore
import com.crewboard.data.SocketClient
import com.crewboard.data.TaskRepository
import com.crewboard.sync.SyncWorker

/** Tiny hand-rolled service locator; avoids a DI framework in a small app. */
class CrewApp : Application() {
    lateinit var settings: SettingsStore
    lateinit var repository: TaskRepository
    private var cachedApi: Pair<String, CrewApi>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "crewboard.db").build()
        repository = TaskRepository(db.taskDao(), { api() }, settings, SocketClient(settings)) {
            SyncWorker.enqueue(this)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SHIFT, "Shift", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TASKS, "New tasks", NotificationManager.IMPORTANCE_HIGH))
    }

    fun api(): CrewApi {
        val url = settings.baseUrl
        cachedApi?.takeIf { it.first == url }?.let { return it.second }
        return ApiFactory.create(url) { settings.token }.also { cachedApi = url to it }
    }

    companion object {
        const val CHANNEL_SHIFT = "shift"
        const val CHANNEL_TASKS = "tasks"
        lateinit var instance: CrewApp
            private set
    }
}
