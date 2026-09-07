package com.atuy.scomb

import android.app.Application
import androidx.room.Room
import com.atuy.scomb.data.db.*
import com.atuy.scomb.data.repository.ScombCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScombCacheTest {
    private lateinit var db: AppDatabase
    private lateinit var cache: ScombCache

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        cache = ScombCache(db)
    }

    @After fun close() = db.close()

    private fun task(id: String, manual: Boolean = false) = Task(
        id, "課題", "授業", 0, 2_000_000_000_000L, "https://example.com", "class", id,
        null, null, manual, false
    )

    private fun cell(period: Int = 0, manual: Boolean = false) = ClassCell(
        "class", period, 0, manual, "2026-1", 2026, "1", "授業", null, null,
        null, null, "note", null, 2, null
    )

    private fun news(unread: Boolean) = NewsItem(
        "news", "", "title", "category", "author", "2026-09-06", "", unread, "url", null
    )

    @Test fun taskRefreshPreservesAndReturnsManualTasks() = runBlocking {
        db.taskDao().insertTasks(listOf(task("old"), task("manual", true)))
        val result = cache.replaceTasks(listOf(task("new")))
        assertEquals(setOf("new", "manual"), result.map { it.id }.toSet())
    }

    @Test fun emptySnapshotRemovesOnlyApiTasks() = runBlocking {
        db.taskDao().insertTasks(listOf(task("old"), task("manual", true)))
        assertEquals(listOf("manual"), cache.replaceTasks(emptyList()).map { it.id })
    }

    @Test fun failedReplacementRollsBackDeletion() = runBlocking {
        db.taskDao().insertOrUpdateTask(task("old"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_tasks BEFORE INSERT ON task_table BEGIN SELECT RAISE(ABORT, 'failure'); END"
        )
        try {
            cache.replaceTasks(listOf(task("new")))
            fail("Expected insertion failure")
        } catch (_: android.database.SQLException) {
            assertEquals(listOf("old"), db.taskDao().getAllTasks().map { it.id })
        }
    }

    @Test fun timetableRefreshPreservesLocalMetadataAndManualCells() = runBlocking {
        db.classCellDao().insertCells(listOf(
            cell().copy(userNote = "local", customLinksJson = "[]"), cell(1, true)
        ))
        val result = cache.replaceTimetable("2026-1", listOf(cell()))
        assertEquals(2, result.size)
        assertTrue(result.any { it.isUserClassCell })
        assertEquals("[]", result.first { !it.isUserClassCell }.customLinksJson)
    }

    @Test fun detailUpdateAffectsEveryOccurrenceWithoutOverwritingLinks() = runBlocking {
        db.classCellDao().insertCells(listOf(cell(), cell(1)))
        db.classCellDao().updateLinks("class", "2026-1", "[]")
        db.classCellDao().updateDetails("class", "2026-1", "new note", 123)
        val cells = db.classCellDao().getCells("2026-1")
        assertTrue(cells.all { it.note == "new note" && it.customLinksJson == "[]" })
    }

    @Test fun explicitUnreadSurvivesRefresh() = runBlocking {
        db.newsItemDao().insertOrUpdateNewsItem(news(true))
        assertTrue(cache.replaceNews(listOf(news(false))).single().unread)
    }

    @Test fun markingRemovedNewsDoesNotResurrectIt() = runBlocking {
        db.newsItemDao().setUnread(listOf("removed"), false)
        assertTrue(db.newsItemDao().getAllNews().isEmpty())
    }

    @Test fun clearingSessionRemovesEveryTable() = runBlocking {
        db.taskDao().insertOrUpdateTask(task("manual", true))
        db.classCellDao().insertClassCell(cell())
        db.newsItemDao().insertOrUpdateNewsItem(news(true))
        cache.clear()
        assertTrue(db.taskDao().getAllTasks().isEmpty())
        assertTrue(db.classCellDao().getAllClasses().isEmpty())
        assertTrue(db.newsItemDao().getAllNews().isEmpty())
    }
}
