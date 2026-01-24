package com.atuy.scomb.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_table")
data class NewsItem(
    @PrimaryKey
    val newsId: String,
    val data2: String,
    val title: String,
    val category: String,
    val domain: String,
    val publishTime: String,
    val tags: String,
    var unread: Boolean,
    val url: String,
    val otkey: String?
)
