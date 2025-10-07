package com.atuy.scomb.data.network

import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.util.DateUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// For POST /login
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

// For generic "OK" responses
@JsonClass(generateAdapter = true)
data class StatusResponse(
    val status: String
)

// For POST /sessionid
@JsonClass(generateAdapter = true)
data class SessionIdRequest(
    val sessionid: String
)

// For GET /api/timetable/{yearMonth}
@JsonClass(generateAdapter = true)
data class ApiClassCell(
    val id: String,
    val name: String,
    val room: String?,
    val teachers: String,
    val period: Int,
    @param:Json(name = "day_of_week") val dayOfWeek: Int
) {
    fun toDbClassCell(year: Int, term: String, timetableTitle: String): ClassCell {
        return ClassCell(
            classId = this.id,
            period = this.period,
            dayOfWeek = this.dayOfWeek,
            isUserClassCell = false,
            timetableTitle = timetableTitle,
            year = year,
            term = term,
            name = this.name,
            teachers = this.teachers,
            room = this.room,
            customColorInt = null,
            url = "https://scombz.shibaura-it.ac.jp/lms/course?idnumber=${this.id}",
            note = null,
            syllabusUrl = null,
            numberOfCredit = null
        )
    }
}

// For GET /api/task/{yearMonth}
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

        return Task(
            id = "$taskType-$classId-$id", // Create a unique ID for DB
            title = title,
            className = from ?: "未設定",
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


// For GET /api/news
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
        val category = tags?.split(",")?.getOrNull(0) ?: "その他"
        val unread = readTime.isNullOrEmpty()
        val url = "https://mobile.scombz.shibaura-it.ac.jp/$title/news/"

        return NewsItem(
            newsId = this.newsId,
            data2 = "", // Not available from API
            title = this.title ?: "タイトルなし",
            category = category,
            domain = this.author ?: "掲載元不明",
            publishTime = this.publishTime ?: "",
            tags = this.tags ?: "",
            unread = unread,
            url = url
        )
    }
}

