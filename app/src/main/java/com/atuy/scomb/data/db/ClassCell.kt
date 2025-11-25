package com.atuy.scomb.data.db

import androidx.room.Entity
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "class_cell",
    primaryKeys = ["classId", "period", "dayOfWeek", "isUserClassCell", "timetableTitle"]
)
data class ClassCell(
    val classId: String,
    val period: Int,
    val dayOfWeek: Int,
    val isUserClassCell: Boolean,
    val timetableTitle: String,
    val year: Int?,
    val term: String?,
    val name: String?,
    val teachers: String?,
    val room: String?,
    val customColorInt: Int?,
    val url: String?,
    val note: String?,
    val syllabusUrl: String?,
    val numberOfCredit: Int?,
    val userNote: String? = null,
    val customLinksJson: String? = null
)

@JsonClass(generateAdapter = true)
data class CustomLink(
    val title: String,
    val url: String
)