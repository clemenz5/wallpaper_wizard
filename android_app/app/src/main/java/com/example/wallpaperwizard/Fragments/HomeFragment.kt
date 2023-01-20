package com.example.wallpaperwizard.Fragments

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.TagsResult
import com.example.wallpaperwizard.WallpaperApi
import com.example.wallpaperwizard.Worker.WallpaperChangerWorker
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.GlobalScope
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors


class HomeFragment : Fragment() {
    private lateinit var _broadcastReceiver: BroadcastReceiver
    val _sdfWatchTime = SimpleDateFormat("HH:mm")
    val _sdfDateTime = SimpleDateFormat("EEEE, dd. MMMM")
    lateinit var time_Viewer: TextView
    lateinit var date_viewer: TextView
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider
    val wallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
    lateinit var tagsResultCallback: Callback<TagsResult>


    object RetrofitHelper {
        val baseUrl = "https://ww.keefer.de/"
        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(parent: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        // Show the notification
        notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        )!!
        notiProvider = NotificationProvider(context)
        notiProvider.createNotificationChannels(notificationManager)
        GlobalScope.run {
            for (workInfo in WorkManager.getInstance(context).getWorkInfosByTag("upload").get()) {
                if (workInfo.state == WorkInfo.State.ENQUEUED) {
                    notificationManager.notify(
                        notiProvider.UPLOAD_PENDING_NOTIFICATION_ID,
                        notiProvider.UPLOAD_PENDING_NOTIFICATION
                    )
                }
                Log.d(
                    "worker_list",
                    WorkManager.getInstance(context).getWorkInfosByTag("upload").get().toString()
                )
            }
        }

        val swipeRefreshView: SwipeRefreshLayout = parent.findViewById(R.id.swipe_refresh_view)
        val applyConfig: FloatingActionButton = parent.findViewById(R.id.applyConfig)
        val main_parent_view: ConstraintLayout = parent.findViewById(R.id.main_parent_view)


        val prefs = context.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val tagsPrefsKey = "tags_preferences"
        val syncPrefsKey = "sync_preferences"

        val preferred_tags = prefs.getString(tagsPrefsKey, "")!!.split(";").stream().filter { str -> str != ""}.collect(Collectors.toList())
        val preferred_sync = prefs.getString(syncPrefsKey, "")

        val syncInput: EditText = parent.findViewById(R.id.sync_input)
        syncInput.setText(preferred_sync)
        val tagGroup: TagGroup = parent.findViewById(R.id.tag_group)
        time_Viewer = parent.findViewById(R.id.time_viewer)
        date_viewer = parent.findViewById(R.id.date_viewer)
        date_viewer.text = _sdfDateTime.format(Date())
        time_Viewer.text = _sdfWatchTime.format(Date())

        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle(){
                Snackbar.make(
                    main_parent_view,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { view ->
                    wallpaperApi.getTags().enqueue(this)
                }.show()
            }

            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                swipeRefreshView.isRefreshing = false
                if (response.code() == 200) {
                    Log.d("tags_response", response.body()!!.tags.toList().toString())
                    tagGroup.setTags(response.body()!!.tags, preferred_tags.toTypedArray())
                } else {
                    errorHandle()
                }
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                swipeRefreshView.isRefreshing = false
                errorHandle()
            }
        }

        wallpaperApi.getTags().enqueue(tagsResultCallback)

        swipeRefreshView.setOnRefreshListener { ->
            wallpaperApi.getTags().enqueue(tagsResultCallback)
        }



        applyConfig.setOnClickListener { view ->
            var saved_tags = ""
            for (id in tagGroup.tagGroup.checkedChipIds) {
                val chip = parent.findViewById<Chip>(id)
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
                    Data.Builder().putStringArray("download_tags", tagGroup.getSelectedTags())
                        .putString("sync", syncString).build()
                ).build()
            WorkManager.getInstance(context).enqueue(downloadWorkRequest)


        }
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
        requireContext().registerReceiver(_broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(_broadcastReceiver)
    }
}

