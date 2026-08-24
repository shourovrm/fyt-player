package com.fyiplayer.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

// One download at a time: a second "Get" tap while fetching would stack identical downloads.
private var activeDownloadUrl: String? = null

/** Fetches the release APK via DownloadManager and hands the finished file to the system package
 *  installer. The user still confirms the install (plus the unknown-sources prompt on first use)
 *  — nothing here installs silently. */
fun downloadAndInstall(context: Context, url: String, versionName: String) {
    if (activeDownloadUrl == url) return
    activeDownloadUrl = url

    // app context: the download outlives whatever Activity/Compose scope kicked it off
    val appContext = context.applicationContext
    val downloadManager = appContext.getSystemService<DownloadManager>() ?: return
    val request = DownloadManager.Request(Uri.parse(url))
        .setMimeType("application/vnd.android.package-archive")
        .setTitle("FYT Player v$versionName")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(appContext, null, "update-$versionName.apk")
    val downloadId = downloadManager.enqueue(request)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
            activeDownloadUrl = null
            ctx.unregisterReceiver(this)
            val apkUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
    ContextCompat.registerReceiver(
        appContext, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_EXPORTED, // system broadcast — API 33+ requires an explicit flag
    )
}
