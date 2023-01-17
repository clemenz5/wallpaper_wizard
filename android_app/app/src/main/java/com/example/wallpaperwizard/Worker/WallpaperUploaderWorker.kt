package com.example.wallpaperwizard.Worker

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.WallpaperApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import java.io.File

class WallpaperUploaderWorker(appContext: Context, val workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    lateinit var notificationManager: NotificationManager
    lateinit var notificationProvider: NotificationProvider
    val notiProvider = NotificationProvider(applicationContext)
    object RetrofitHelper {
        val baseUrl = "https://ww.keefer.de/"
        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .build()
        }
    }

    override fun doWork(): Result {
        Log.d("worker_list", WorkManager.getInstance(applicationContext).getWorkInfosByTag("upload").get().toString())

        // Show the notification
        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)

        notificationManager.cancel(notiProvider.UPLOAD_PENDING_NOTIFICATION_ID)
        notificationManager.notify(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID, notiProvider.UPLOAD_RUNNING_NOTIFICATION)

        val wallpaperApi =
            WallpaperChangerWorker.RetrofitHelper.getInstance().create(WallpaperApi::class.java)
        val upload_file = File(inputData.getString("upload_file_url"))
        val request_file = upload_file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val multipart_file =
            MultipartBody.Part.createFormData("image", upload_file.name, request_file);
        val request = wallpaperApi.uploadWallpaper(multipart_file, "upload_test")
        Log.d("upload_request", request.request().body!!.toString())
        val response = request.execute()

        if (response.isSuccessful) {
            Log.d("tags_response", response.toString())
            if (response.code() != 200) {
                notificationManager.notify(1, notiProvider.UPLOAD_FAILURE_NOTIFICATION)
                notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
                return Result.failure()
            }
            notificationManager.notify(1, notiProvider.UPLOAD_SUCCESS_NOTIFICATION)
            notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
            return Result.success()

        } else {
            Log.d("upload_response", response.toString())
            notificationManager.notify(1, notiProvider.UPLOAD_FAILURE_NOTIFICATION)
            notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
            return Result.failure()
        }
    }
}
