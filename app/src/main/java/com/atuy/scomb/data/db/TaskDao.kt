package com.atuy.scomb.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTask(task: Task)

    @Query("SELECT * FROM task_table ORDER BY deadline ASC")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM task_table WHERE id = :id")
    suspend fun getTask(id: String): Task?

    @Query("DELETE FROM task_table WHERE id = :id")
    suspend fun removeTask(id: String)

    @Query("SELECT * FROM task_table WHERE classId = :classId ORDER BY deadline ASC")
    suspend fun getTasksByClassId(classId: String): List<Task>
}