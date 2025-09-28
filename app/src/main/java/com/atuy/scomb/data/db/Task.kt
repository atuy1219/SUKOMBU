package com.atuy.scomb.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 課題情報を表すエンティティ（データベースのテーブル設計図）
 */
@Entity(tableName = "task_table") // テーブル名を指定
data class Task(
    @PrimaryKey // この項目が主キーであることを示す
    val id: String,
    val title: String,
    val className: String,
    val taskType: Int,
    val deadline: Long, // 日時はミリ秒単位のLong型で保存
    val url: String,
    val classId: String,
    val reportId: String,
    val customColor: Int?, // 色が設定されない場合もあるのでNull許容
    val addManually: Boolean,
    var done: Boolean
)