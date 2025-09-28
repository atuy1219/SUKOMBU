package com.atuy.scomb.di

import android.content.Context
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.network.ScombzScraper
import com.atuy.scomb.data.repository.ScombzRepository
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
    fun provideAppDatabase(@ApplicationContext context: Context) = AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase) = db.taskDao()

    @Provides
    @Singleton
    fun provideClassCellDao(db: AppDatabase) = db.classCellDao()

    @Provides
    @Singleton
    fun provideScombzScraper() = ScombzScraper()

    @Provides
    @Singleton
    fun provideScombzRepository(
        taskDao: com.atuy.scomb.data.db.TaskDao,
        classCellDao: com.atuy.scomb.data.db.ClassCellDao,
        scraper: ScombzScraper,
        @ApplicationContext context: Context
    ): ScombzRepository {
        return ScombzRepository(taskDao, classCellDao, scraper, context)
    }
}