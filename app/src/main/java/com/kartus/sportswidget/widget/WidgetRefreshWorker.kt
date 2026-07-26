package com.kartus.sportswidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kartus.sportswidget.data.ServiceLocator
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widget's stored scoreboard.
 *
 * Runs on a 15-minute period, which is WorkManager's floor for periodic work
 * ([MIN_PERIOD_MINUTES]) — the platform will not schedule anything tighter, and
 * asking for it just gets silently clamped. Live-by-the-second updating is the
 * app's job, not the widget's.
 *
 * The system decides when within each period the work actually runs, and Doze will
 * stretch that further when the phone is idle. That is expected: the widget shows
 * how old its numbers are rather than pretending they are current.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.repository(applicationContext)

        return repository.scoreboard(LocalDate.now(), force = true).fold(
            onSuccess = { scoreboard ->
                WidgetState.publish(applicationContext, scoreboard)
                Result.success()
            },
            onFailure = {
                // Leave the previously published snapshot in place and let WorkManager
                // back off — a transient StatsAPI blip should not blank the widget.
                Result.retry()
            },
        )
    }

    companion object {
        const val MIN_PERIOD_MINUTES = 15L

        private const val PERIODIC_WORK = "widget-refresh-periodic"
        private const val ONE_SHOT_WORK = "widget-refresh-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Idempotent: safe to call on every widget placement. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                MIN_PERIOD_MINUTES, TimeUnit.MINUTES,
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        /** Fired by the widget's refresh button. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
