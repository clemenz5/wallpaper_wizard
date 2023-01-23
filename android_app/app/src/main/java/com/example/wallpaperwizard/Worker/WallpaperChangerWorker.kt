package com.example.wallpaperwizard.Worker

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.WallpaperApi
import okhttp3.internal.notify
import retrofit2.Retrofit


class WallpaperChangerWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    lateinit var notificationManager: NotificationManager
    val notiProvider = NotificationProvider(applicationContext)
    object RetrofitHelper {
        val baseUrl = "https://ww.keefer.de/"

        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .build()
        }
    }

    override fun doWork(): Result {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Wallpaper Update"
            val description: String = "Notifies that the wallpaper is currently being updated"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("1", name, importance)
            channel.description = description
            val notificationManager: NotificationManager = getSystemService(
                this.applicationContext, NotificationManager::class.java
            )!!
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)
        notificationManager.cancel(notiProvider.DOWNLOAD_PENDING_NOTIFICATION_ID)
        notificationManager.notify(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID, notiProvider.DOWNLOAD_RUNNING_NOTIFICATION)

        var tags_string = inputData.getStringArray("download_tags")!!.joinToString(prefix = "", separator = ";", postfix="")

        val wallpaperApi =
            RetrofitHelper.getInstance().create(WallpaperApi::class.java)
        val request = wallpaperApi.getWallpaper(
            sync = inputData.getString("sync")!!,
            tags = tags_string
        )
        Log.d("request_url_tags", tags_string)

        Log.d("request_url", request.request().url.toString())
        val response = request.execute()


        if (response.isSuccessful) {
            Log.d("tags_response", response.toString())
            if (response.code() == 200 || response.code() == 304) {
                val inputStream = response.body()!!.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                val wallpaperManager = WallpaperManager.getInstance(this.applicationContext)
                wallpaperManager.setBitmap(bitmap)
                notificationManager.notify(1, notiProvider.DOWNLOAD_SUCCESS_NOTIFICATION)
                notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
                return Result.success()
            }
            notificationManager.notify(1, notiProvider.DOWNLOAD_FAILURE_NOTIFICATION)
            notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
            return Result.failure()

        } else {
            Log.d("download_response", response.toString())
            notificationManager.notify(1, notiProvider.DOWNLOAD_FAILURE_NOTIFICATION)
            notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
            return Result.failure()
        }
    }
}
