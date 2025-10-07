package com.atuy.scomb.data.network

import android.util.Log
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.util.DateUtils
import com.atuy.scomb.util.ScrapingFailedException
import com.atuy.scomb.util.SessionExpiredException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import java.util.Calendar
import javax.inject.Inject

object ScombzConstant {
    const val SCOMBZ_DOMAIN = "https://scombz.shibaura-it.ac.jp"
    // Task
    const val TASK_LIST_CSS_CLASS_NM = "result_list_line"
    const val TASK_LIST_DEADLINE_CULUMN_CSS_NM = "tasklist-deadline"
    // Timetable
    const val TIMETABLE_ROW_CSS_CLASS_NM = "div-table-data-row"
    const val TIMETABLE_CELL_CSS_CLASS_NM = "div-table-cell"
    const val TIMETABLE_CELL_HEADER_CSS_CLASS_NM = "timetable-course-top-btn"
    const val TIMETABLE_CELL_DETAIL_CSS_CLASS_NM = "div-table-cell-detail"
    // Survey
    const val SURVEY_ROW_CSS_NM = "result-list"
    // News
    const val NEWS_LIST_ITEM_CSS_NM = "result-list"
    const val NEWS_LIST_ITEM_TITLE_CSS_NM = "link-txt"
    const val NEWS_CATEGORY_CSS_NM = "portal-information-list-type"
    const val NEWS_DOMAIN_CSS_NM = "portal-information-list-division"
    const val NEWS_PUBLISH_TIME_CSS_NM = "portal-information-list-date"
    // Community
    const val COMMUNITY_NAME_LINK_CSS_NM = "a.linkToCommunitytop" // 修正: より正確なセレクタに変更
}

class ScombzScraper @Inject constructor(private val api: ScombzApiService) {
    private val TAG = "ScombzScraper"
    private fun throwIfSessionExpired(html: String) {
        val document = Jsoup.parse(html)
        if (document.getElementById("userNameInput") != null) {
            throw SessionExpiredException()
        }
    }

    suspend fun fetchTimetable(sessionId: String, year: Int, term: String): List<ClassCell> {
        val response = api.getTimetable("SESSION=$sessionId", year, term)

        if (!response.isSuccessful || response.body() == null) {
            Log.w(TAG, "Failed to fetch timetable. Code: ${response.code()}")
            throw ScrapingFailedException("Failed to get HTML for Timetable (Code: ${response.code()})")
        }

        val html = response.body()!!.string()
        throwIfSessionExpired(html)

        val document = Jsoup.parse(html)
        val timetableRows = document.getElementsByClass(ScombzConstant.TIMETABLE_ROW_CSS_CLASS_NM)
        val timetableTitle = "$year-$term"
        val classCells = mutableListOf<ClassCell>()

        timetableRows.forEachIndexed { period, row ->
            val cells = row.getElementsByClass(ScombzConstant.TIMETABLE_CELL_CSS_CLASS_NM)
            cells.forEachIndexed { dayOfWeek, cell ->
                if (cell.children().isEmpty()) return@forEachIndexed

                val header =
                    cell.getElementsByClass(ScombzConstant.TIMETABLE_CELL_HEADER_CSS_CLASS_NM)
                        .firstOrNull() ?: return@forEachIndexed
                val detail =
                    cell.getElementsByClass(ScombzConstant.TIMETABLE_CELL_DETAIL_CSS_CLASS_NM)
                        .firstOrNull() ?: return@forEachIndexed

                val classId = header.id()
                val name = header.text()
                val room = detail.child(0).attr("title")
                val teachers = detail.child(0).children().filter { it.tagName() == "span" }
                    .joinToString(", ") { it.text() }

                classCells.add(
                    ClassCell(
                        classId = classId,
                        period = period,
                        dayOfWeek = dayOfWeek,
                        isUserClassCell = false,
                        timetableTitle = timetableTitle,
                        year = year,
                        term = term,
                        name = name,
                        teachers = teachers,
                        room = room,
                        customColorInt = null,
                        url = "${ScombzConstant.SCOMBZ_DOMAIN}/lms/course?idnumber=$classId",
                        note = null,
                        syllabusUrl = null,
                        numberOfCredit = null
                    )
                )
            }
        }
        return classCells
    }

    suspend fun fetchTasks(sessionId: String): List<Task> {
        try {
            val response = api.getTaskList("SESSION=$sessionId")

            val html = response.body()?.string() ?: throw ScrapingFailedException("Empty response body")

            throwIfSessionExpired(html)

            val document = Jsoup.parse(html)
            val taskRows = document.getElementsByClass(ScombzConstant.TASK_LIST_CSS_CLASS_NM)

            return taskRows.mapNotNull { row ->
                try {
                    if (row.children().size < 5) return@mapNotNull null
                    val className = row.child(0).text()
                    val titleElement = row.child(2).children().firstOrNull() ?: return@mapNotNull null
                    val title = titleElement.text()
                    val url = ScombzConstant.SCOMBZ_DOMAIN + titleElement.attr("href")
                    val deadlineText =
                        row.getElementsByClass(ScombzConstant.TASK_LIST_DEADLINE_CULUMN_CSS_NM)
                            .firstOrNull()?.child(1)?.text() ?: ""
                    val deadline = DateUtils.stringToTime(deadlineText)

                    val uri = java.net.URI(url)
                    val queryParams = uri.query.split("&")
                        .associate { val parts = it.split("="); parts[0] to parts.getOrNull(1) }
                    val classId = queryParams["idnumber"] ?: ""
                    val reportId = queryParams["reportId"] ?: queryParams["examinationId"] ?: ""

                    val taskTypeText = row.child(1).text()
                    val taskType = when {
                        taskTypeText.contains("課題") -> 0
                        taskTypeText.contains("テスト") -> 1
                        taskTypeText.contains("アンケート") -> 2
                        else -> 3
                    }

                    Task(
                        id = "$taskType-$classId-$reportId", title = title, className = className,
                        taskType = taskType, deadline = deadline, url = url, classId = classId,
                        reportId = reportId, customColor = null, addManually = false, done = false
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ScombzScraper", "Error fetching tasks", e)
            throw e
        }
    }

    suspend fun fetchSurveys(sessionId: String): List<Task> {
        val response = api.getSurveyList("SESSION=$sessionId")
        val html = response.body()?.string() ?: throw ScrapingFailedException("Failed to get HTML for Surveys")

        throwIfSessionExpired(html)

        val document = Jsoup.parse(html)
        val surveyRows = document.getElementsByClass(ScombzConstant.SURVEY_ROW_CSS_NM)

        return surveyRows.mapNotNull { row ->
            try {
                if (row.children().size < 7) return@mapNotNull null
                val surveyId = row.child(0).attr("value")
                val classId = row.child(1).attr("value")
                val isDone =
                    row.child(2).select(".portal-surveys-status").any { it.text() == "済み" }
                val title = row.child(2).child(0).textNodes().firstOrNull()?.text()?.trim() ?: ""
                val surveyDomain = row.child(5).text()
                val deadlineText = row.child(3).child(2).text()
                val deadline = DateUtils.stringToTime(deadlineText, format = "yyyy/MM/dd HH:mm")

                val url = if (classId.isEmpty()) {
                    "${ScombzConstant.SCOMBZ_DOMAIN}/portal/surveys/take?surveyId=$surveyId"
                } else {
                    "${ScombzConstant.SCOMBZ_DOMAIN}/lms/course/surveys/take?idnumber=$classId&surveyId=$surveyId"
                }

                Task(
                    id = "2-$classId-$surveyId", // 2: アンケート
                    title = title,
                    className = surveyDomain,
                    taskType = 2,
                    deadline = deadline,
                    url = url,
                    classId = classId,
                    reportId = surveyId,
                    customColor = null,
                    addManually = false,
                    done = isDone
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun fetchLmsIdMap(sessionId: String): Map<String, String> {
        return try {
            val calendar = Calendar.getInstance()
            val year = if (calendar.get(Calendar.MONTH) < 3) calendar.get(Calendar.YEAR) - 1 else calendar.get(Calendar.YEAR)
            val term = if (calendar.get(Calendar.MONTH) in 3..8) "1" else "2"
            fetchTimetable(sessionId, year, term)
                .filter { it.name != null }
                .associate { it.name!! to it.classId }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch LMS ID map, proceeding without it.", e)
            emptyMap()
        }
    }

    private suspend fun fetchCommunityIdMap(sessionId: String): Map<String, String> {
        return try {
            val response = api.getCommunityList("SESSION=$sessionId")

            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "Failed to fetch community list. Code: ${response.code()}")
                return emptyMap()
            }
            val html = response.body()!!.string()

            throwIfSessionExpired(html)
            val document = Jsoup.parse(html)
            // 修正: <a>タグを直接選択し、そのid属性からIDを取得する
            val communityElements = document.select(ScombzConstant.COMMUNITY_NAME_LINK_CSS_NM)
            communityElements.mapNotNull { element ->
                val name = element.text()
                val id = element.attr("id").ifEmpty { null }
                if (name.isNotEmpty() && id != null) {
                    name to id
                } else {
                    null
                }
            }.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch Community ID map, proceeding without it.", e)
            emptyMap()
        }
    }

    suspend fun fetchNews(sessionId: String): List<NewsItem> = coroutineScope {
        val lmsIdMapDeferred = async { fetchLmsIdMap(sessionId) }
        val communityIdMapDeferred = async { fetchCommunityIdMap(sessionId) }

        val cookie = "SESSION=$sessionId"
        val initialResponse = api.getNewsListPage(cookie)
        val initialHtml = initialResponse.body()?.string()
            ?: throw ScrapingFailedException("Failed to get initial news page HTML")
        throwIfSessionExpired(initialHtml)
        val initialDoc = Jsoup.parse(initialHtml)
        val csrfToken = initialDoc.select("input[name=_csrf]").attr("value")

        if (csrfToken.isEmpty()) {
            throw ScrapingFailedException("CSRF token not found")
        }

        val response = api.searchNewsList(cookie, csrfToken)
        val html = response.body()?.string() ?: throw ScrapingFailedException("Failed to get searched news page HTML")
        throwIfSessionExpired(html)

        val document = Jsoup.parse(html)
        val newsRows = document.getElementsByClass(ScombzConstant.NEWS_LIST_ITEM_CSS_NM)

        val lmsIdMap = lmsIdMapDeferred.await()
        val communityIdMap = communityIdMapDeferred.await()

        return@coroutineScope newsRows.mapNotNull { row ->
            try {
                val titleElement = row.getElementsByClass(ScombzConstant.NEWS_LIST_ITEM_TITLE_CSS_NM).firstOrNull()
                    ?: return@mapNotNull null
                val newsId = titleElement.attr("data1").ifBlank { "" }
                val data2 = titleElement.attr("data2").ifBlank { "" }
                val title = titleElement.text().trim()
                val category = row.getElementsByClass(ScombzConstant.NEWS_CATEGORY_CSS_NM).firstOrNull()?.text()?.trim() ?: ""
                val domain = row.getElementsByClass(ScombzConstant.NEWS_DOMAIN_CSS_NM).firstOrNull()?.text()?.trim() ?: ""
                val publishTime = row.getElementsByClass(ScombzConstant.NEWS_PUBLISH_TIME_CSS_NM).firstOrNull()
                    ?.child(0)
                    ?.text()
                    ?.trim()
                    ?: ""

                var idnumber = ""
                if (category == "LMS") {
                    idnumber = lmsIdMap[domain] ?: ""
                } else if (category == "COMMUNITY") {
                    idnumber = communityIdMap[domain] ?: ""
                }

                val url = "${ScombzConstant.SCOMBZ_DOMAIN}/portal/home/information/detail_direct?informationId=$newsId&selectCategoryCd=$data2&idnumber=$idnumber"

                NewsItem(
                    newsId = newsId,
                    data2 = data2,
                    title = title,
                    category = category,
                    domain = domain,
                    publishTime = publishTime,
                    tags = "",
                    unread = true,
                    url = url
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse a news row.", e)
                null
            }
        }
    }
}

