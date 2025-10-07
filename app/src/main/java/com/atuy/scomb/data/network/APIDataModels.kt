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
    val userId: String,
    val userPw: String
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

// For GET /api/timetable/{yearMonth} - Structure is an assumption
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

// For GET /api/task/{yearMonth} - Structure is an assumption
@JsonClass(generateAdapter = true)
data class ApiTask(
    val id: String,
    val title: String,
    @param:Json(name = "course_name") val courseName: String,
    @param:Json(name = "task_type") val taskType: String, // "課題", "テスト", "アンケート"
    val deadline: String, // "YYYY-MM-DD HH:mm"
    val url: String,
    val done: Boolean,
    @param:Json(name = "course_id") val courseId: String,
    @param:Json(name = "report_id") val reportId: String
) {
    fun toDbTask(): Task {
        return Task(
            id = this.id,
            title = this.title,
            className = this.courseName,
            taskType = when (this.taskType) {
                "課題" -> 0
                "テスト" -> 1
                "アンケート" -> 2
                else -> 3
            },
            deadline = DateUtils.stringToTime(this.deadline, "yyyy-MM-dd HH:mm"),
            url = this.url,
            classId = this.courseId,
            reportId = this.reportId,
            customColor = null,
            addManually = false,
            done = this.done
        )
    }
}

// For GET /api/news - Structure is an assumption
@JsonClass(generateAdapter = true)
data class ApiNewsItem(
    val id: String,
    val title: String,
    val category: String,
    val domain: String,
    @param:Json(name = "publish_time") val publishTime: String,
    val unread: Boolean,
    val url: String,
    val data2: String
) {
    fun toDbNewsItem(): NewsItem {
        return NewsItem(
            newsId = this.id,
            data2 = this.data2,
            title = this.title,
            category = this.category,
            domain = this.domain,
            publishTime = this.publishTime,
            tags = "",
            unread = this.unread,
            url = this.url
        )
    }
}
