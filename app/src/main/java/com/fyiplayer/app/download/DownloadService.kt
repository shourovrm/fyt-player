package com.fyiplayer.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.fyiplayer.app.MainActivity
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "fyi_downloads"
private const val NOTIF_ID = 4201
private const val ACTION_PAUSE = "com.fyiplayer.app.download.PAUSE"
private const val ACTION_CANCEL = "com.fyiplayer.app.download.CANCEL"
private const val EXTRA_PAGE_URL = "pageUrl"

/**
 * Foreground service draining [DownloadQueue] one row at a time (the queue's own semaphore caps
 * it at one engine process). The notification can't be hidden and a user blocking notifications
 * doesn't suppress it, so it always carries the real title, percent and pause/cancel actions --
 * never a page URL or media URL, and never an apologetic placeholder.
 */
class DownloadService : Service() {
    private lateinit var queue: DownloadQueue
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var lastNotifiedPercent = -1

    override fun onCreate() {
        super.onCreate()
        queue = DownloadQueue.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_PAGE_URL)?.let { pageUrl -> scope.launch { queue.pause(pageUrl) } }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_PAGE_URL)?.let { pageUrl -> scope.launch { queue.cancel(pageUrl) } }
        }
        startForeground(NOTIF_ID, buildNotification(null))
        if (loopJob?.isActive != true) {
            loopJob = scope.launch {
                queue.resetStale()
                while (isActive) {
                    // Reset per row: without this a row that starts at, say, 40% (resumed) would
                    // suppress every tick below the previous row's last-notified percent.
                    lastNotifiedPercent = -1
                    val progressed = queue.processNext(
                        onProgress = { _, progress -> updateNotification(progress) },
                        onFinished = { item, state -> postFinishedNotification(item, state) },
                    )
                    if (!progressed) break
                }
                // Queue drained: take the ongoing notification down with it, or it lingers
                // showing the last row's stale "Starting…"/percent even though nothing is running
                // (device-verified bug this fixes).
                stopForeground(STOP_FOREGROUND_REMOVE)
                // stopSelf(startId), not bare stopSelf(): if a fresh enqueue delivered a newer
                // onStartCommand while this loop was finishing up, the system no-ops this call
                // instead of tearing the service down out from under that new work.
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15 caps a `dataSync` foreground service's total run time. At the cap the system
     * calls this; if the service isn't stopped within a few seconds it kills the app with an
     * unhandled foreground-service-timeout exception. So: stop the drain loop, park the running
     * row as PAUSED (resumable via `--continue` on the next start), and take the notification
     * down with the service.
     *
     * Runs on its own scope: [scope] dies with [onDestroy], and blocking here would eat the few
     * seconds we're given. If the process dies before the park write lands, [DownloadQueue.resetStale]
     * requeues the still-RUNNING row on the next start anyway.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        loopJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch { queue.pauseActive() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun activeItem() = queue.rows.value.firstOrNull { it.state == DownloadState.RUNNING }

    private fun updateNotification(progress: DownloadProgress) {
        val percent = progress.percent?.toInt()
        if (percent != null && percent == lastNotifiedPercent) return
        if (percent != null) lastNotifiedPercent = percent
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(percent))
    }

    private fun buildNotification(percent: Int?): Notification {
        val item = activeItem()
        val title = item?.ref?.title ?: "Downloading"
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (percent != null) "$percent%" else "Starting…")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (percent != null) setProgress(100, percent, false) }
        item?.let {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE, it.ref.pageUrl))
            builder.addAction(android.R.drawable.ic_delete, "Cancel", actionIntent(ACTION_CANCEL, it.ref.pageUrl))
        }
        return builder.build()
    }

    /** One-shot, non-ongoing notification per finished row -- id derived from the page URL (not
     *  [NOTIF_ID]) so it neither collides with nor gets cleared by the ongoing/next-row progress
     *  notification, and multiple finished rows can stack instead of overwriting each other. Opens
     *  the app plainly: no Downloads-tab deep link exists yet to target more precisely. */
    private fun postFinishedNotification(item: DownloadItem, state: DownloadState) {
        val done = state == DownloadState.COMPLETED
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_warning)
            .setContentTitle(if (done) "Download complete" else "Download failed")
            .setContentText(if (done) "Downloaded: ${item.ref.title}" else "Download failed: ${item.ref.title}")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(item.ref.pageUrl.hashCode(), notif)
    }

    private fun actionIntent(action: String, pageUrl: String): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_PAGE_URL, pageUrl)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }
}
