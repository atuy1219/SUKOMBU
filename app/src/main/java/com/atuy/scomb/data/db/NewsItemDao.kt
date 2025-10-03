package com.atuy.scomb.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NewsItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNewsItem(newsItem: NewsItem)

    @Query("SELECT * FROM news_table ORDER BY publishTime DESC")
    suspend fun getAllNews(): List<NewsItem>

    @Query("SELECT * FROM news_table WHERE newsId = :newsId")
    suspend fun getNews(newsId: String): NewsItem?

    @Query("DELETE FROM news_table")
    suspend fun clearAll()
}