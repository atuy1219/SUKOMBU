package com.atuy.scomb.di

import android.content.Context
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.network.ScombzApiService
import com.atuy.scomb.data.network.ScombzScraper
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

private const val BASE_URL = "https://scombz.shibaura-it.ac.jp/"
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context) = AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase) = db.taskDao()

    @Provides
    @Singleton
    fun provideClassCellDao(db: AppDatabase) = db.classCellDao()

    @Provides
    @Singleton
    fun provideNewsItemDao(db: AppDatabase) = db.newsItemDao()

    // ▼▼▼ ここから下の通信関連のメソッドが不足していました ▼▼▼

    // User-Agentヘッダーを追加するOkHttpClientの作り方を定義
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()
    }

    // 上記のOkHttpClientを使ったRetrofitの作り方を定義
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()
    }

    // 上記のRetrofitを使ったScombzApiServiceの作り方を定義
    @Provides
    @Singleton
    fun provideScombzApiService(retrofit: Retrofit): ScombzApiService {
        return retrofit.create(ScombzApiService::class.java)
    }

    // ▲▲▲ ▲▲▲

    @Provides
    @Singleton
    fun provideScombzScraper(apiService: ScombzApiService): ScombzScraper {
        return ScombzScraper(apiService)
    }

    @Provides
    @Singleton
    fun provideScombzRepository(
        taskDao: com.atuy.scomb.data.db.TaskDao,
        classCellDao: com.atuy.scomb.data.db.ClassCellDao,
        newsItemDao: com.atuy.scomb.data.db.NewsItemDao,
        scraper: ScombzScraper,
        sessionManager: com.atuy.scomb.data.SessionManager
    ): ScombzRepository {
        return ScombzRepository(
            taskDao,
            classCellDao,
            newsItemDao,
            scraper,
            sessionManager
        )
    }
}