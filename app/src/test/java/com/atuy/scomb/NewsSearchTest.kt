package com.atuy.scomb

import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.ui.viewmodel.AdvancedNewsSearchInput
import com.atuy.scomb.ui.viewmodel.NewsSearchMatcher
import org.junit.Assert.*
import org.junit.Test

class NewsSearchTest {
    @Test fun advancedSearchTreatsOperatorsAsLiteralWords() {
        for (word in listOf("-test", "title:report", "since:2026", "OR")) {
            val matcher = NewsSearchMatcher.parse(AdvancedNewsSearchInput(allWords = word).toQuery())
            assertNull(matcher.error)
            val item = NewsItem("id", "", word, "", "", "2026-09-06", "", true, "", null)
            assertTrue(matcher.matches(item))
            assertFalse(matcher.matches(item.copy(title = "unrelated")))
        }
    }
}
