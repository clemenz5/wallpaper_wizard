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
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.wallpaperwizard.WallpaperApi
import retrofit2.Retrofit


class WallpaperChangerWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
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

        // Create the notification
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this.applicationContext, "1")
                .setSmallIcon(R.drawable.sym_def_app_icon)
                .setContentTitle("Wallpaper Wizard")
                .setContentText("Loading new Wallpaper")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(100, 0, true)

        // Show the notification
        val notificationManager = NotificationManagerCompat.from(this.applicationContext)
        notificationManager.notify(0, builder.build())

        var tags_string = ""
        for (tag in inputData.getStringArray("tags")!!){
            tags_string += "$tag;"
        }

        val wallpaperApi =
            RetrofitHelper.getInstance().create(WallpaperApi::class.java)
        val request = wallpaperApi.getWallpaper(
            sync = inputData.getString("sync")!!,
            tags = tags_string
        )
        Log.d("request_url_tags", tags_string)

        Log.d("request_url", request.request().url.toString())
        val result = request.execute()



        val inputStream = result.body()!!.byteStream()
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val wallpaperManager = WallpaperManager.getInstance(this.applicationContext)
        wallpaperManager.setBitmap(bitmap)

        notificationManager.cancel(0)


        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }
}
