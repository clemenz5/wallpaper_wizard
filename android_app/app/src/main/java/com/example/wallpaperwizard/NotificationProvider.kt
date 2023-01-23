package com.example.wallpaperwizard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationProvider(private val context: Context) {

    val UPLOAD_PENDING_NOTIFICATION_ID = 1000
    val UPLOAD_RUNNING_NOTIFICATION_ID = 1001
    val DOWNLOAD_PENDING_NOTIFICATION_ID = 1002
    val DOWNLOAD_RUNNING_NOTIFICATION_ID = 1003
    val UPLOAD_CHANNEL_ID = "upload_channel"
    val DOWNLOAD_CHANNEL_ID = "upload_channel"

    val DOWNLOAD_PENDING_NOTIFICATION =
        NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Waiting for network connection")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setProgress(100, 0, true).build()

    val DOWNLOAD_SUCCESS_NOTIFICATION =
        NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Successfully set Wallpaper")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

    val DOWNLOAD_FAILURE_NOTIFICATION =
        NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Failed to set your Wallpaper :/")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

    val DOWNLOAD_RUNNING_NOTIFICATION =
        NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Loading Wallpaper")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setProgress(100, 0, true).build()

    val UPLOAD_SUCCESS_NOTIFICATION =
        NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Successfully uploaded Wallpaper")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

    val UPLOAD_FAILURE_NOTIFICATION =
        NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Failed to upload your Wallpaper :/")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

    val UPLOAD_PENDING_NOTIFICATION =
        NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Waiting for network connection")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setProgress(100, 0, true).build()

    val UPLOAD_RUNNING_NOTIFICATION =
        NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID).setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(context.getString(R.string.app_name)).setContentText("Uploading Wallpaper")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setProgress(100, 0, true).build()

    fun createNotificationChannels(notificationManager: NotificationManager){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val upload_channel = NotificationChannel(UPLOAD_CHANNEL_ID, "Wallpaper Upload", NotificationManager.IMPORTANCE_DEFAULT)
            upload_channel.description = "Notifies that the wallpaper is currently being uploaded"
            notificationManager.createNotificationChannel(upload_channel)

            val download_channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, "Wallpaper Download", NotificationManager.IMPORTANCE_DEFAULT)
            upload_channel.description = "Notifies that the wallpaper is currently being downloaded"
            notificationManager.createNotificationChannel(upload_channel)
        }
    }
}