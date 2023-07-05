package com.example.wallpaperwizard.Worker

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.RetrofitHelper
import com.example.wallpaperwizard.WallpaperApi
import retrofit2.Retrofit


class WallpaperChangerWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    lateinit var notificationManager: NotificationManager
    val notiProvider = NotificationProvider(applicationContext)

    override fun doWork(): Result {

        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)
        notificationManager.cancel(notiProvider.DOWNLOAD_PENDING_NOTIFICATION_ID)
        notificationManager.notify(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID, notiProvider.DOWNLOAD_RUNNING_NOTIFICATION)

        var tags_string = inputData.getStringArray("download_tags")!!.joinToString(prefix = "", separator = ";", postfix="")
        var sync_string = inputData.getString("sync")!!

        Log.d("WallpaperChangerWorker:${this.id}", "doWork was called")
        Log.d("WallpaperChangerWorker:${this.id}", "Tags: $tags_string")
        Log.d("WallpaperChangerWorker:${this.id}", "Sync: $sync_string")

        val wallpaperApi =
            RetrofitHelper.getInstance().create(WallpaperApi::class.java)

        val request = wallpaperApi.getWallpaper(
            sync = sync_string,
            tags = tags_string
        )
        Log.d("WallpaperChangerWorker:${this.id}", "Calling API at ${request.request().url}")

        val response = request.execute()
        Log.d("WallpaperChangerWorker:${this.id}", "Response: $response")


        if (response.isSuccessful) {
            if (response.code() == 200 || response.code() == 304) {
                Log.d("WallpaperChangerWorker:${this.id}", "Response is successful and valid")
                val inputStream = response.body()!!.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                val wallpaperManager = WallpaperManager.getInstance(this.applicationContext)
                val crop = response.headers().get("crop")?.split(",")
                if (crop != null && crop.size == 4) {
                    Log.d("WallpaperChangerWorker:${this.id}", "Crop: $crop")
                    val crop_rect = Rect(crop.get(0).toFloat().toInt(), crop.get(1).toFloat().toInt(), crop.get(2).toFloat().toInt(), crop.get(3).toFloat().toInt())
                    wallpaperManager.setBitmap(bitmap, crop_rect, true)
                } else {
                    Log.d("WallpaperChangerWorker:${this.id}", "Crop: Not found")
                    wallpaperManager.setBitmap(bitmap, null, true)
                }
                notificationManager.notify(1, notiProvider.DOWNLOAD_SUCCESS_NOTIFICATION)
                notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
                return Result.success()
            } else {
                Log.d("WallpaperChangerWorker:${this.id}", "Response is not valid")
                notificationManager.notify(1, notiProvider.DOWNLOAD_FAILURE_NOTIFICATION)
                notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
                return Result.failure()
            }


        } else {
            Log.d("WallpaperChangerWorker:${this.id}", "Response is not successful")
            notificationManager.notify(1, notiProvider.DOWNLOAD_FAILURE_NOTIFICATION)
            notificationManager.cancel(notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID)
            return Result.failure()
        }
    }
}
