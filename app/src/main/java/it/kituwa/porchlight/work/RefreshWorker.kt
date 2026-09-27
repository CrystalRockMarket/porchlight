package it.kituwa.porchlight.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.kituwa.porchlight.data.Reachability
import it.kituwa.porchlight.data.Severity
import it.kituwa.porchlight.data.SnapshotStore
import it.kituwa.porchlight.data.PorchlightClient
import it.kituwa.porchlight.data.ServerStore
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val snapshotStore = SnapshotStore(context)
        val client = PorchlightClient(ServerStore(context), snapshotStore)

        val snapshots = runCatching { client.refreshAll() }.getOrElse { return Result.retry() }

        val critical = snapshots.sumOf { snapshot ->
            snapshot.issues.count { it.severity == Severity.CRITICAL }
        }
        val warning = snapshots.sumOf { snapshot ->
            snapshot.issues.count { it.severity != Severity.CRITICAL }
        }
        val unreachable = snapshots.count {
            it.reachability == Reachability.UNREACHABLE || it.reachability == Reachability.AUTH_FAILED
        }

        Notifications.ensureChannel(context)
        Notifications.showSummary(context, critical, warning, unreachable)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "porchlight.refresh"

        fun schedule(context: Context) {
            Notifications.ensureChannel(context)
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
