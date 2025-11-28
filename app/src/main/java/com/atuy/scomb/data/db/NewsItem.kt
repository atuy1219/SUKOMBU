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
    val readTime: String?, // 既読時刻。未読の場合はnull
    val url: String
) {
    // 互換性や利便性のためのヘルパープロパティ
    val unread: Boolean
        get() = readTime.isNullOrEmpty()
}