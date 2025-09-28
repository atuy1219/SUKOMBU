package com.atuy.scomb.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideScombzRepository(
        taskDao: TaskDao,
        scraper: ScombzScraper,
        @ApplicationContext context: Context
    ): ScombzRepository {
        return ScombzRepository(taskDao, scraper, context)
    }

    // ... 他のProvides
}