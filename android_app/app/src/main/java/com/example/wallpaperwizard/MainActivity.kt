package com.example.wallpaperwizard

import android.Manifest
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewParent
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.navigation.ui.AppBarConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import com.example.wallpaperwizard.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var _broadcastReceiver: BroadcastReceiver
    val _sdfWatchTime = SimpleDateFormat("HH:mm")
    val _sdfDateTime = SimpleDateFormat("EEEE, dd. MMMM")
    lateinit var time_Viewer: TextView
    lateinit var date_viewer: TextView
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider

    object RetrofitHelper {

        val baseUrl = "https://ww.keefer.de/"

        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        // Show the notification
        notificationManager = ContextCompat.getSystemService(
            applicationContext, NotificationManager::class.java
        )!!
        notiProvider = NotificationProvider(applicationContext)
        notiProvider.createNotificationChannels(notificationManager)
        GlobalScope.run {
            for (workInfo in WorkManager.getInstance(applicationContext).getWorkInfosByTag("upload")
                .get()) {
                if (workInfo.state == WorkInfo.State.ENQUEUED) {
                    notificationManager.notify(
                        notiProvider.UPLOAD_PENDING_NOTIFICATION_ID,
                        notiProvider.UPLOAD_PENDING_NOTIFICATION
                    )
                }
                Log.d(
                    "worker_list",
                    WorkManager.getInstance(applicationContext).getWorkInfosByTag("upload").get()
                        .toString()
                )
            }
        }

        val permission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions, 1212)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val swipeRefreshView: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_view)
        val applyConfig: FloatingActionButton = findViewById(R.id.applyConfig)
        val main_parent_view: RelativeLayout = findViewById(R.id.main_parent_view)
        val add_wallpaper_button: FloatingActionButton = findViewById(R.id.addWallpaper)
        add_wallpaper_button.setOnClickListener { view ->
            this@MainActivity.startActivity(Intent(this@MainActivity, UploadActivity::class.java))
        }


        val prefs = getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val tagsPrefsKey = "tags_preferences"
        val syncPrefsKey = "sync_preferences"

        val preferred_tags = prefs.getString(tagsPrefsKey, "")!!.split(";")
        val preferred_sync = prefs.getString(syncPrefsKey, "")

        val syncInput: EditText = findViewById(R.id.sync_input)
        syncInput.setText(preferred_sync)
        val tagGroup: ChipGroup = findViewById(R.id.tag_group)
        time_Viewer = findViewById(R.id.time_viewer)
        date_viewer = findViewById(R.id.date_viewer)
        date_viewer.text = _sdfDateTime.format(Date())
        time_Viewer.text = _sdfWatchTime.format(Date())

        fetchPopulateTags(preferred_tags, tagGroup, main_parent_view)
        swipeRefreshView.setOnRefreshListener { ->
            fetchPopulateTags(preferred_tags, tagGroup, main_parent_view)
            swipeRefreshView.isRefreshing = false
        }



        applyConfig.setOnClickListener { view ->
            var saved_tags = ""
            for (id in tagGroup.checkedChipIds) {
                val chip = findViewById<Chip>(id)
                saved_tags += chip.text
                saved_tags += ";"
            }
            prefs.edit().putString(tagsPrefsKey, saved_tags).apply()
            prefs.edit().putString(syncPrefsKey, syncInput.text.toString()).apply()

            Snackbar.make(view, "Set new Wallpaper", Snackbar.LENGTH_LONG).setAction("Action", null)
                .show()
            val syncString = syncInput.text.toString()
            val downloadWorkRequest: WorkRequest =
                OneTimeWorkRequestBuilder<WallpaperChangerWorker>().setInputData(
                    Data.Builder().putStringArray("tags", preferred_tags.toTypedArray())
                        .putString("sync", syncString).build()
                ).build()
            WorkManager.getInstance(this).enqueue(downloadWorkRequest)


        }
    }

    fun fetchPopulateTags(
        preferred_tags: List<String>,
        tagGroup: ChipGroup,
        main_parent_view: View
    ) {
        val wallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
        wallpaperApi.getTags().enqueue(object : Callback<TagsResult> {
            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                Log.d("tags_response", response.toString())
                if (response.code() != 200) {
                    Snackbar.make(
                        main_parent_view,
                        "Error while getting the Tags - Server returned " + response.code()
                            .toString(),
                        Snackbar.LENGTH_LONG
                    ).setAction("Retry") { view ->
                        fetchPopulateTags(
                            preferred_tags,
                            tagGroup,
                            main_parent_view
                        )
                    }.show()
                    return
                }
                val tags = response.body()!!.tags

                Log.d("network_response", tags.toList().toString())
                tagGroup.removeAllViews()
                for (tag in tags) {
                    val view = Chip(
                        this@MainActivity,
                        null,
                        com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter
                    )
                    view.text = tag
                    view.id = ViewCompat.generateViewId()
                    view.isCheckable = true
                    view.isClickable = true
                    if (tag in preferred_tags) {
                        view.isChecked = true
                    }
                    runOnUiThread {
                        tagGroup.addView(view)
                    }
                }
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                Snackbar.make(
                    main_parent_view,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { view ->
                    fetchPopulateTags(
                        preferred_tags,
                        tagGroup,
                        main_parent_view
                    )
                }.show()
            }

        })
    }

    override fun onStart() {
        super.onStart()
        _broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action!!.compareTo(Intent.ACTION_TIME_TICK) == 0 && time_Viewer.isInLayout) {
                    time_Viewer.text = _sdfWatchTime.format(Date())
                    date_viewer.text = _sdfDateTime.format(Date())

                }
            }
        }
        registerReceiver(_broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(_broadcastReceiver)
    }


}

