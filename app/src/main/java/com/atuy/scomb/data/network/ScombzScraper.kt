package com.atuy.scomb.data.network

import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.util.DateUtils
import org.jsoup.Jsoup
import retrofit2.Retrofit
import java.lang.Exception

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
    const val NEWS_LIST_ITEM_CSS_NM = "contents-display-flex"
    const val NEWS_LIST_ITEM_TITLE_CSS_NM = "link-txt"
    const val NEWS_CATEGORY_CSS_NM = "portal-information-list-type"
    const val NEWS_DOMAIN_CSS_NM = "portal-information-list-division"
    const val NEWS_PUBLISH_TIME_CSS_NM = "portal-information-list-date"
}

class ScombzScraper {
    private val api = Retrofit.Builder()
        .baseUrl(ScombzConstant.SCOMBZ_DOMAIN)
        .build()
        .create(ScombzApiService::class.java)

    suspend fun fetchTimetable(sessionId: String, year: Int, term: String): List<ClassCell> {
        // (前回実装したコード)
        val response = api.getTimetable("SESSION=$sessionId", year, term)
        val html = response.body()?.string() ?: throw Exception("Failed to get HTML for Timetable")
        if (html.contains("ログイン")) throw Exception("Session expired")

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
                        .first() ?: return@forEachIndexed
                val detail =
                    cell.getElementsByClass(ScombzConstant.TIMETABLE_CELL_DETAIL_CSS_CLASS_NM)
                        .first() ?: return@forEachIndexed

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
        // (前回実装したコード)
        val response = api.getTaskList("SESSION=$sessionId")
        val html = response.body()?.string() ?: throw Exception("Failed to get HTML for Tasks")
        if (html.contains("ログイン")) throw Exception("Session expired")

        val document = Jsoup.parse(html)
        val taskRows = document.getElementsByClass(ScombzConstant.TASK_LIST_CSS_CLASS_NM)

        return taskRows.mapNotNull { row ->
            try {
                if (row.children().size < 5) return@mapNotNull null
                val className = row.child(0).text()
                val titleElement = row.child(2).child(0)
                val title = titleElement.text()
                val url = ScombzConstant.SCOMBZ_DOMAIN + titleElement.attr("href")
                val deadlineText =
                    row.getElementsByClass(ScombzConstant.TASK_LIST_DEADLINE_CULUMN_CSS_NM)
                        .first()?.child(1)?.text() ?: ""
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
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun fetchSurveys(sessionId: String): List<Task> {
        val response = api.getSurveyList("SESSION=$sessionId")
        val html = response.body()?.string() ?: throw Exception("Failed to get HTML for Surveys")
        if (html.contains("ログイン")) throw Exception("Session expired")

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
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun fetchNews(sessionId: String): List<NewsItem> {
        val response = api.getNewsList("SESSION=$sessionId")
        val html = response.body()?.string() ?: throw Exception("Failed to get HTML for News")
        if (html.contains("ログイン")) throw Exception("Session expired")

        val document = Jsoup.parse(html)
        val newsRows = document.getElementsByClass(ScombzConstant.NEWS_LIST_ITEM_CSS_NM)

        return newsRows.mapNotNull { row ->
            try {
                val titleElement = row.getElementsByClass(ScombzConstant.NEWS_LIST_ITEM_TITLE_CSS_NM).first()
                    ?: return@mapNotNull null

                val newsId = titleElement.attr("data1").ifBlank { "" }
                val data2 = titleElement.attr("data2").ifBlank { "" }
                val title = titleElement.text().trim()

                val category = row.getElementsByClass(ScombzConstant.NEWS_CATEGORY_CSS_NM).first()?.text()?.trim() ?: ""
                val domain = row.getElementsByClass(ScombzConstant.NEWS_DOMAIN_CSS_NM).first()?.text()?.trim() ?: ""
                val publishTime = row.getElementsByClass(ScombzConstant.NEWS_PUBLISH_TIME_CSS_NM).first()
                    ?.child(0)
                    ?.text()
                    ?.trim()
                    ?: ""

                // タグ抽出の例（存在しなければ空文字）
                val tags = "" // 必要なら HTML から抽出する処理を書く

                // 未読フラグの初期値（別ロジックがあれば変更）
                val unread = true

                NewsItem(
                    newsId = newsId,
                    data2 = data2,
                    title = title,
                    category = category,
                    domain = domain,
                    publishTime = publishTime,
                    tags = tags,
                    unread = unread
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}