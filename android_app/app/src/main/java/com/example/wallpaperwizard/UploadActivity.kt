package com.example.wallpaperwizard

import RealPathUtil
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.work.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ortiz.touchview.TouchImageView
import java.util.*
import java.util.concurrent.TimeUnit


class UploadActivity : AppCompatActivity() {
    var upload_file_url = ""
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        notiProvider = NotificationProvider(applicationContext)
        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)
        Log.d(
            "worker_list",
            WorkManager.getInstance(this).getWorkInfosByTag("upload").get().toString()
        )

        val wallpaper_preview = findViewById<TouchImageView>(R.id.wallpaper_preview)

        val button_select_wallpaper = findViewById<FloatingActionButton>(R.id.select_wallpaper)
        val button_upload_wallpaper = findViewById<FloatingActionButton>(R.id.upload_wallpaper)

        val startForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("selection", result.data!!.data.toString())
                val source = ImageDecoder.createSource(this.contentResolver, result.data!!.data!!)
                val bitmap = ImageDecoder.decodeBitmap(source)

                var filePath: String = RealPathUtil.getRealPath(this, result.data!!.data!!)!!
                upload_file_url = filePath
                wallpaper_preview.setImageBitmap(
                    bitmap
                )
            }
        }
        button_select_wallpaper.setOnClickListener { view ->
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startForResult.launch(intent)

        }

        button_upload_wallpaper.setOnClickListener { view ->
            // Create the notification
            Log.d(
                "worker_list",
                WorkManager.getInstance(this).getWorkInfosByTag("upload").get().toString()
            )
            val constraints: Constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val upload_work_request: WorkRequest =
                OneTimeWorkRequestBuilder<WallpaperUploaderWorker>().setInputData(
                    Data.Builder().putString("upload_file_url", upload_file_url)
                        .putString("rect", wallpaper_preview.zoomedRect.toString()).build()
                ).setConstraints(constraints).addTag("upload")
                    .build()
            WorkManager.getInstance(this).enqueue(upload_work_request)

            WorkManager.getInstance(this).getWorkInfoByIdLiveData(upload_work_request.id)
                .observeForever { workInfo ->
                    if (workInfo != null && workInfo.state == WorkInfo.State.ENQUEUED) {
                        val notificationManager =
                            NotificationManagerCompat.from(this.applicationContext)
                        notificationManager.notify(notiProvider.UPLOAD_PENDING_NOTIFICATION_ID, notiProvider.UPLOAD_PENDING_NOTIFICATION)
                    }
                    if (workInfo != null && workInfo.state.isFinished) {
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {

                        } else {

                        }
                    }
                }

        }

    }

    override fun onStop() {
        super.onStop()
    }
}