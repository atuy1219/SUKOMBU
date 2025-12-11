package com.atuy.scomb.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.atuy.scomb.BuildConfig
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.network.ScombzApiService
import com.squareup.moshi.Moshi
// import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory // 依存関係エラー回避のためコメントアウト
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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
            .fallbackToDestructiveMigration(dropAllTables = true)
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
    @Suppress("unused")
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            // .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideScombzApiService(moshi: Moshi): ScombzApiService {
        return Retrofit.Builder()
            .baseUrl("https://scombz.shibaura-it.ac.jp/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ScombzApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return logging
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}