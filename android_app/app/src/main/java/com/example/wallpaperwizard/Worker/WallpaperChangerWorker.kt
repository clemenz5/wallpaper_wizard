package com.example.wallpaperwizard.Worker

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
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
        val baseUrl = "https://ww.keefer.de"

        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .build()
        }
    }

    override fun doWork(): Result {

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
                val crop = response.headers().get("crop")?.split(",")

                if (crop != null && crop.size == 4) {
                    Log.d("crop_rect", crop.get(0).toFloat().toString())
                    val crop_rect = Rect(crop.get(0).toFloat().toInt(), crop.get(1).toFloat().toInt(), crop.get(2).toFloat().toInt(), crop.get(3).toFloat().toInt())
                    Log.d("crop_rect", crop_rect.toString())
                    wallpaperManager.setBitmap(bitmap, crop_rect, true)
                } else {
                    wallpaperManager.setBitmap(bitmap, null, true)
                }
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
