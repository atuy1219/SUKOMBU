package com.atuy.scomb.di

import android.content.Context
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.network.ScombzApiService
import com.atuy.scomb.data.repository.ScombzRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        return OkHttpClient.Builder()
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
}
