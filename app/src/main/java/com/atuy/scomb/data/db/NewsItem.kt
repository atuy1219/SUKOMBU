package com.atuy.scomb.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

private const val UNKNOWN_NEWS_SOURCE = "掲載元不明"

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

val NewsItem.hasSourceName: Boolean
    get() = domain.isNotBlank() && domain != UNKNOWN_NEWS_SOURCE
