package com.atuy.scomb.di

import android.content.Context
import com.atuy.scomb.BuildConfig
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.network.ScombzApiService
import com.atuy.scomb.data.repository.ScombzRepository
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

private const val BASE_URL = "https://smob.sic.shibaura-it.ac.jp/smob/api/"

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

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authManager: AuthManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()

            if (originalRequest.url.encodedPath.endsWith("/login")) {
                return@Interceptor chain.proceed(originalRequest)
            }

            val token = runBlocking { authManager.authTokenFlow.first() }
            val requestBuilder = originalRequest.newBuilder()
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideScombzApiService(retrofit: Retrofit): ScombzApiService {
        return retrofit.create(ScombzApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideScombzRepository(
        taskDao: com.atuy.scomb.data.db.TaskDao,
        classCellDao: com.atuy.scomb.data.db.ClassCellDao,
        newsItemDao: com.atuy.scomb.data.db.NewsItemDao,
        apiService: ScombzApiService,
        authManager: AuthManager
    ): ScombzRepository {
        return ScombzRepository(
            taskDao,
            classCellDao,
            newsItemDao,
            apiService,
            authManager
        )
    }

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }
}

