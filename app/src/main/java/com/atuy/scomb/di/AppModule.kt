package com.atuy.scomb.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.network.ScombzApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

// DataStoreの拡張プロパティを定義（ファイル名: auth_prefs.preferences_pb）
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "scomb_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideClassCellDao(database: AppDatabase): ClassCellDao {
        return database.classCellDao()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideNewsItemDao(database: AppDatabase): NewsItemDao {
        return database.newsItemDao()
    }

    @Provides
    @Singleton
    fun provideScombzApiService(): ScombzApiService {
        return Retrofit.Builder()
            .baseUrl("https://scombz.shibaura-it.ac.jp/") // ベースURLは仮、実際のリクエストでフルURLを指定するなら無視されるか調整が必要
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScombzApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}