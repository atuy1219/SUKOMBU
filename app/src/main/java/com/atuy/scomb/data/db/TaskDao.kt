package com.atuy.scomb.data.db

import androidx.room.*

/**
 * TaskテーブルにアクセスするためのDAO (Data Access Object)
 */
@Dao
interface TaskDao {
    // onConflict = OnConflictStrategy.REPLACE : 主キーが同じデータがあれば上書きする
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTask(task: Task) // suspendキーワードで非同期処理を示す

    @Query("SELECT * FROM task_table ORDER BY deadline ASC") // 締め切り順で取得
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM task_table WHERE id = :id")
    suspend fun getTask(id: String): Task?

    @Query("DELETE FROM task_table WHERE id = :id")
    suspend fun removeTask(id: String)
}