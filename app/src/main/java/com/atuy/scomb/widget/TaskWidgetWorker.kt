package com.atuy.scomb.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.SessionExpiredException
import com.atuy.scomb.util.ClientException
import com.atuy.scomb.widget.TaskWidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class TaskWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScombzRepository
) : CoroutineWorker(context, workerParams) {
    companion object {
        val tasksStateKey = TaskWidgetUpdater.tasksStateKey
        val loadingStateKey = TaskWidgetUpdater.loadingStateKey
    }

    override suspend fun doWork(): Result = try {
        repository.getTasksAndSurveys(forceRefresh = true)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SessionExpiredException) {
        Result.failure()
    } catch (e: ClientException) {
        Result.failure()
    } catch (e: Exception) {
        android.util.Log.w("TaskWidgetWorker", "Sync failed", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
