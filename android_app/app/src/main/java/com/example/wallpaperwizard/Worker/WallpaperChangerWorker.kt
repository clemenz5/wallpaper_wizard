package com.example.wallpaperwizard.Worker

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.RetrofitHelper
import com.example.wallpaperwizard.WallpaperApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class WallpaperChangerWorker(private val appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    lateinit var notificationManager: NotificationManager
    val notiProvider = NotificationProvider(applicationContext)

    override fun doWork(): Result {

        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)
        notificationManager.cancel(notiProvider.DOWNLOAD_PENDING_NOTIFICATION_ID)
        notificationManager.notify(
            notiProvider.DOWNLOAD_RUNNING_NOTIFICATION_ID,
            notiProvider.DOWNLOAD_RUNNING_NOTIFICATION
        )

        val tagsString = inputData.getStringArray("download_tags")!!
            .joinToString(prefix = "", separator = ";", postfix = "")
        val syncString = inputData.getString("sync")!!

        Log.d("WallpaperChangerWorker:${this.id}", "doWork was called")
        Log.d("WallpaperChangerWorker:${this.id}", "Tags: $tagsString")
        Log.d("WallpaperChangerWorker:${this.id}", "Sync: $syncString")

        val wallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)

        val request = wallpaperApi.getWallpaper(
            sync = syncString, tags = tagsString, follow = false, info = true
        )
        Log.d("WallpaperChangerWorker:${this.id}", "Calling API at ${request.request().url}")

        val response = request.execute()
        Log.d("WallpaperChangerWorker:${this.id}", "Response: $response")


        if (response.isSuccessful) {
            if (response.code() == 204 || response.code() == 304) {
                Log.d("WallpaperChangerWorker:${this.id}", "Response is successful and valid")

                //Check if image is available on the device
                val imagePath: String =
                    appContext.filesDir.absolutePath + "/" + response.headers()["name"]
                val imageFile = File(imagePath)

                var bitmap: Bitmap
                if (imageFile.exists()) {
                    bitmap = BitmapFactory.decodeFile(imagePath)
                } else {
                    // Load image from Server
                    val imageResponse = wallpaperApi.getWallpaperByName(
                        response.headers()["name"]!!
                    ).execute()
                    val inputStream = imageResponse.body()!!.byteStream()
                    bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    // Save the new image
                    try {
                        FileOutputStream(File(imagePath)).use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            outputStream.flush()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                val wallpaperManager = WallpaperManager.getInstance(this.applicationContext)

                val cropRect: Rect? = extractCrop(response.headers()["crop"])
                wallpaperManager.setBitmap(bitmap, cropRect, true)

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

    private fun extractCrop(cropString: String?): Rect? {
        val crop = cropString?.split(",")
        if (crop != null && crop.size == 4) {
            Log.d("WallpaperChangerWorker:${this.id}", "Crop: $crop")
            return Rect(
                crop[0].toFloat().toInt(),
                crop[1].toFloat().toInt(),
                crop[2].toFloat().toInt(),
                crop[3].toFloat().toInt()
            )
        } else {
            Log.d("WallpaperChangerWorker:${this.id}", "Crop: Not found")
            return null
        }
    }
}
