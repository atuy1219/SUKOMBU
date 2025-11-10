package com.atuy.scomb.data.network

import android.util.Base64
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.util.DateUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

private fun String?.decodeBase64(): String? {
    if (this.isNullOrBlank()) return this
    return try {
        val flags = Base64.DEFAULT or Base64.NO_PADDING
        String(Base64.decode(this, flags), Charsets.UTF_8)
    } catch (e: Exception) {
        this
    }
}

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @param:Json(name = "user") val userId: String,
    @param:Json(name = "pass") val userPw: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val status: String,
    @param:Json(name = "user_type") val userType: String?,
    val gakubu: String?,
    val gakka: String?,
    val token: String?,
    val terms: List<Term>?
)

@JsonClass(generateAdapter = true)
data class Term(
    val year: Int,
    val start: List<String>
)

@JsonClass(generateAdapter = true)
data class StatusResponse(
    val status: String
)

@JsonClass(generateAdapter = true)
data class SessionIdRequest(
    val sessionid: String
)

@JsonClass(generateAdapter = true)
data class ApiClassCell(
    @param:Json(name = "classId")
    val id: String,
    val name: String,
    val room: String?,
    val teachers: String,
    val period: Int,
    @param:Json(name = "day_of_week") val dayOfWeek: Int
) {
    fun toDbClassCell(year: Int, term: String, timetableTitle: String): ClassCell {
        val decodedName = name.decodeBase64() ?: "授業名なし"
        val decodedRoom = room.decodeBase64()
        val decodedTeachers = teachers.decodeBase64() ?: ""

        return ClassCell(
            classId = this.id,
            period = this.period,
            dayOfWeek = this.dayOfWeek,
            isUserClassCell = false,
            timetableTitle = timetableTitle,
            year = year,
            term = term,
            name = decodedName,
            teachers = decodedTeachers,
            room = decodedRoom,
            customColorInt = null,
            url = "https://scombz.shibaura-it.ac.jp/lms/course?idnumber=${this.id}",
            note = null,
            syllabusUrl = null,
            numberOfCredit = null
        )
    }
}

@JsonClass(generateAdapter = true)
data class ApiTask(
    @Json(name = "taskType") val taskType: Int?,
    val id: String?,
    @Json(name = "classId") val classId: String?,
    val from: String?,
    val title: String?,
    val done: Int?,
    @Json(name = "submitTimeTo") val submitTimeTo: String?
) {
    fun toDbTask(): Task? {
        // Essential fields for a task to be valid
        if (id == null || classId == null || title == null || submitTimeTo == null || taskType == null) {
            return null
        }

        val url = when (taskType) {
            0 -> "https://scombz.shibaura-it.ac.jp/lms/course/reports/take?idnumber=$classId&reportId=$id" // 課題
            1 -> "https://scombz.shibaura-it.ac.jp/lms/course/examinations/take?idnumber=$classId&examinationId=$id" // テスト
            2 -> "https://scombz.shibaura-it.ac.jp/lms/course/surveys/take?idnumber=$classId&surveyId=$id" // アンケート
            else -> "https://scombz.shibaura-it.ac.jp/lms/course?idnumber=$classId"
        }

        val decodedTitle = title.decodeBase64() ?: "タイトルなし"
        val decodedClassName = if (from.isNullOrBlank()) "未設定" else from

        return Task(
            id = "$taskType-$classId-$id",
            title = decodedTitle,
            className = decodedClassName,
            taskType = taskType,
            deadline = DateUtils.stringToTime(submitTimeTo, "yyyy-MM-dd HH:mm:ss"),
            url = url,
            classId = classId,
            reportId = id,
            customColor = null,
            addManually = false,
            done = (done ?: 0) == 1
        )
    }
}


@JsonClass(generateAdapter = true)
data class ApiNewsItem(
    @param:Json(name = "newsId") val newsId: String,
    @param:Json(name = "classId") val classId: String?,
    val title: String?,
    val author: String?,
    @Json(name = "publishTime") val publishTime: String?,
    val tags: String?,
    @Json(name = "readTime") val readTime: String?
) {
    fun toDbNewsItem(): NewsItem {
        val decodedTags = this.tags
        val category = decodedTags?.split(",")?.getOrNull(0) ?: "その他"
        val unread = readTime.isNullOrEmpty()
        val url = "https://mobile.scombz.shibaura-it.ac.jp/$title/news/"

        val decodedTitle = this.title.decodeBase64() ?: "タイトルなし"
        val decodedDomain = this.author.decodeBase64() ?: "掲載元不明"

        return NewsItem(
            newsId = this.newsId,
            data2 = "",
            title = decodedTitle,
            category = category,
            domain = decodedDomain,
            publishTime = this.publishTime ?: "",
            tags = decodedTags ?: "",
            unread = unread,
            url = url
        )
    }
}
