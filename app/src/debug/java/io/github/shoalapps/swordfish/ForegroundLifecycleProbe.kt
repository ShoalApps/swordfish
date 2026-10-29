package io.github.shoalapps.swordfish

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.awaitCancellation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Debug-only fixture: no YouTube requests, files or real downloads. Never packaged in releases. */
class ForegroundTestActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = "Foreground lifecycle test" })
    }
}

class ForegroundLifecycleProbe(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Download lifecycle test", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Download lifecycle test").setOngoing(true).build()
        try {
            setForeground(ForegroundInfo(id.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            started += id
            awaitCancellation()
        } finally {
            started -= id
        }
    }
    companion object {
        const val CHANNEL = "foreground-lifecycle-test"
        val started: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    }
}
