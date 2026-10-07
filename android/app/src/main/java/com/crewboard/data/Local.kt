package com.crewboard.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val description: String,
    val requiredSkill: String,
    val zone: String,
    val priority: Int,
    val status: String,
    val assignedTo: Int?,
)

/** A status change made while offline; replayed by the repository / SyncWorker. */
@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Int,
    val status: String,
)

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, id")
    abstract fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract fun observe(id: Int): Flow<TaskEntity?>

    @Upsert abstract suspend fun upsertAll(tasks: List<TaskEntity>)
    @Upsert abstract suspend fun upsertOne(task: TaskEntity)
    @Query("UPDATE tasks SET status = :status WHERE id = :id") abstract suspend fun setStatus(id: Int, status: String)
    @Query("DELETE FROM tasks WHERE id = :id") abstract suspend fun delete(id: Int)
    @Query("DELETE FROM tasks") abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(tasks: List<TaskEntity>) {
        clear()
        upsertAll(tasks)
    }

    @Insert abstract suspend fun enqueue(action: PendingAction)
    @Query("SELECT * FROM pending_actions ORDER BY id") abstract suspend fun pending(): List<PendingAction>
    @Delete abstract suspend fun remove(action: PendingAction)
}

@Database(entities = [TaskEntity::class, PendingAction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
