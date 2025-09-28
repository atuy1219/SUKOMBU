package com.atuy.scomb.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * アプリケーションのRoomデータベース
 * entities = { ... } には、このDBが管理する全てのエンティティクラスを列挙する
 */
@Database( entities = [Task::class, ClassCell::class, NewsItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // このDBがどのDAOを持っているかを定義
    abstract fun taskDao(): TaskDao
    abstract fun classCellDao(): ClassCellDao    // <- 追加
    abstract fun newsItemDao(): NewsItemDao
    // abstract fun classCellDao(): ClassCellDao ...

    companion object {
        // @Volatile: このインスタンスが常にメインメモリから読み書きされることを保証
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // シングルトンパターンでデータベースインスタンスを取得
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scomb_database" // DBファイル名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}