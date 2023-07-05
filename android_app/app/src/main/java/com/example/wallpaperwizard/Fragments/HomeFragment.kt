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
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import com.example.wallpaperwizard.*
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.Worker.WallpaperChangerWorker
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors


class HomeFragment : Fragment() {
    private lateinit var _broadcastReceiver: BroadcastReceiver
    val sdfWatchTime = SimpleDateFormat("HH:mm")
    val sdfDateTime = SimpleDateFormat("EEEE, dd. MMMM")
    lateinit var timeViewer: TextView
    lateinit var dateViewer: TextView
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider
    val wallpaperApi: WallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
    lateinit var tagsResultCallback: Callback<TagsResult>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onViewCreated(parent: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        // Notification Setup
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
        swipeRefreshView.setOnRefreshListener { ->
            wallpaperApi.getTags().enqueue(tagsResultCallback)
        }

        val mainParentView: ConstraintLayout = parent.findViewById(R.id.main_parent_view)

        val settingsFab: FloatingActionButton = parent.findViewById(R.id.action_settings)
        settingsFab.setOnClickListener {
            startActivity(Intent(activity, SettingsActivity::class.java))
        }

        val prefs = context.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val tagsPrefsKey = "tags_preferences"
        val syncPrefsKey = "sync_preferences"
        val preferredSync = prefs.getString(syncPrefsKey, "")

        val syncInput: EditText = parent.findViewById(R.id.sync_input)
        syncInput.setText(preferredSync)

        val preferredTags =
            prefs.getString(tagsPrefsKey, "")!!.split(";").stream().filter { str -> str != "" }
                .collect(Collectors.toList())
        val tagGroup: TagGroup = parent.findViewById(R.id.tag_group)
        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle() {
                Snackbar.make(
                    mainParentView,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") {
                    wallpaperApi.getTags().enqueue(this)
                }.show()
            }

            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                swipeRefreshView.isRefreshing = false
                if (response.code() == 200) {
                    Log.d("tags_response", response.body()!!.tags.toList().toString())
                    tagGroup.setTags(response.body()!!.tags, preferredTags.toTypedArray())
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

        timeViewer = parent.findViewById(R.id.time_viewer)
        timeViewer.text = sdfWatchTime.format(Date())
        dateViewer = parent.findViewById(R.id.date_viewer)
        dateViewer.text = sdfDateTime.format(Date())

        val applyConfig: FloatingActionButton = parent.findViewById(R.id.applyConfig)
        applyConfig.setOnClickListener { view ->
            var savedTags = ""
            for (id in tagGroup.tagGroup.checkedChipIds) {
                val chip = parent.findViewById<Chip>(id)
                savedTags += chip.text
                savedTags += ";"
            }
            prefs.edit().putString(tagsPrefsKey, savedTags).apply()
            prefs.edit().putString(syncPrefsKey, syncInput.text.toString()).apply()

            Snackbar.make(view, "Set new Wallpaper", Snackbar.LENGTH_LONG).setAction("Action", null)
                .show()
            val syncString = syncInput.text.toString()
            val constraints: Constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val downloadWorkRequest: OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<WallpaperChangerWorker>().setInputData(
                    Data.Builder().putStringArray("download_tags", tagGroup.getSelectedTags())
                        .putString("sync", syncString).build()
                ).setConstraints(constraints).build()

            // Stop periodic updating
            WorkManager.getInstance(context).cancelUniqueWork("periodic_updater")
            settings.edit().putBoolean("daily", false).apply()


            WorkManager.getInstance(context).enqueueUniqueWork(
                "one_time_updater", ExistingWorkPolicy.REPLACE, downloadWorkRequest
            )

            WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorkRequest.id)
                .observeForever { workInfo ->
                    if (workInfo != null && workInfo.state == WorkInfo.State.ENQUEUED) {
                        notificationManager = ContextCompat.getSystemService(
                            context, NotificationManager::class.java
                        )!!
                        notificationManager.notify(
                            notiProvider.DOWNLOAD_PENDING_NOTIFICATION_ID,
                            notiProvider.DOWNLOAD_PENDING_NOTIFICATION
                        )
                    }
                    if (workInfo != null && workInfo.state.isFinished) {
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {

                        } else {

                        }
                    }
                }
        }
    }


    override fun onStart() {
        super.onStart()
        _broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action!!.compareTo(Intent.ACTION_TIME_TICK) == 0 && timeViewer.isInLayout) {
                    timeViewer.text = sdfWatchTime.format(Date())
                    dateViewer.text = sdfDateTime.format(Date())

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

