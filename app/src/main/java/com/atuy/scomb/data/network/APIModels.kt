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
    @Json(name = "user") val userId: String,
    @Json(name = "pass") val userPw: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val status: String,
    @Json(name = "user_type") val userType: String?,
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
data class OtkeyResponse(
    val status: String,
    val otkey: String?
)

@JsonClass(generateAdapter = true)
data class ApiClassCell(
    @param:Json(name = "classId") val id: String,
    val name: String,
    val room: String?,
    val teachers: String,
    val period: Int,
    val dayOfWeek: Int,
    val syllabusUrl: String?,
    val numberOfCredit: Int?,
    val note: String?,
    val customColor: String?
) {
    fun toDbClassCell(
        year: Int,
        term: String,
        timetableTitle: String,
        otkey: String,
        existingUserNote: String? = null,
        existingCustomLinks: String? = null
    ): ClassCell {
        val decodedName = name.ifBlank { "授業名なし" }
        val decodedRoom = room
        val decodedTeachers = teachers.ifBlank { "" }

        val appDayOfWeek = this.dayOfWeek - 1

        val decodedNote = note.decodeBase64()

        val colorInt = try {
            if (customColor.isNullOrBlank() || customColor == "null") {
                null
            } else {
                customColor.toLongOrNull()?.toInt()
            }
        } catch (e: Exception) {
            null
        }

        return ClassCell(
            classId = this.id,
            period = this.period - 1,
            dayOfWeek = appDayOfWeek,
            isUserClassCell = false,
            timetableTitle = timetableTitle,
            year = year,
            term = term,
            name = decodedName,
            teachers = decodedTeachers,
            room = decodedRoom,
            customColorInt = colorInt,
            url = "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=${this.id}",
            note = decodedNote,
            syllabusUrl = syllabusUrl,
            numberOfCredit = numberOfCredit,
            otkey = otkey,
            userNote = existingUserNote,
            customLinksJson = existingCustomLinks
        )
    }
}

@JsonClass(generateAdapter = true)
data class ApiUpdateClassRequest(
    val classId: String,
    val customizedNumberOfCredit: Int = 0,
    val note: String?,
    val customColor: String? = null
)

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
    fun toDbTask(otkey: String): Task? {
        if (id == null || classId == null || title == null || submitTimeTo == null || taskType == null) {
            return null
        }

        val url = when (taskType) {
            0 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/report/submission?idnumber=$classId&reportId=$id" // 課題
            1 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/examination/taketop?idnumber=$classId&examinationId=$id" // テスト
            2 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/surveys/take?idnumber=$classId&surveyId=$id" // アンケート
            else -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=$classId"
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
            otkey = otkey,
            addManually = false,
            done = (done ?: 0) == 1
        )
    }
}


@JsonClass(generateAdapter = true)
data class ApiNewsItem(
    @Json(name = "newsId") val newsId: String,
    @Json(name = "classId") val classId: String?,
    val title: String?,
    val author: String?,
    @Json(name = "publishTime") val publishTime: String?,
    val tags: String?,
    @Json(name = "readTime") val readTime: String?
) {
    fun toDbNewsItem(otkey: String , yearApiTerm: String): NewsItem {
        val decodedTags = this.tags
        val category = decodedTags?.split(",")?.getOrNull(0) ?: "その他"
        val unread = readTime.isNullOrEmpty()
        val url = "https://mobile.scombz.shibaura-it.ac.jp/news/$yearApiTerm$newsId?"

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