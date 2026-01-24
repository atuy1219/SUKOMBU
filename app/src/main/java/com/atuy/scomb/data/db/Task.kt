package com.atuy.scomb.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "task_table")
data class Task(
    @PrimaryKey
    val id: String,
    val title: String,
    val className: String,
    val taskType: Int,
    val deadline: Long,
    val url: String,
    val classId: String,
    val reportId: String,
    val customColor: Int?,
    val otkey: String?,
    val addManually: Boolean,
    var done: Boolean
)