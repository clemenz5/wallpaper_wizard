package com.example.wallpaperwizard.Fragments

import android.accounts.NetworkErrorException
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
import android.widget.RadioGroup.OnCheckedChangeListener
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import com.example.wallpaperwizard.*
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.Worker.WallpaperChangerWorker
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.ChipGroup.OnCheckedStateChangeListener
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


class HomeFragment : Fragment() {
    private lateinit var _broadcastReceiver: BroadcastReceiver
    val _sdfWatchTime = SimpleDateFormat("HH:mm")
    val _sdfDateTime = SimpleDateFormat("EEEE, dd. MMMM")
    lateinit var time_Viewer: TextView
    lateinit var date_viewer: TextView
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider
    var selected_tags: MutableList<Chip> = mutableListOf()
    var unselected_tags: MutableList<Chip> = mutableListOf()


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
        val main_parent_view: RelativeLayout = parent.findViewById(R.id.main_parent_view)
        val add_wallpaper_button: FloatingActionButton = parent.findViewById(R.id.addWallpaper)
        add_wallpaper_button.setOnClickListener { view ->
        }


        val prefs = context.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val tagsPrefsKey = "tags_preferences"
        val syncPrefsKey = "sync_preferences"

        val preferred_tags = prefs.getString(tagsPrefsKey, "")!!.split(";")
        val preferred_sync = prefs.getString(syncPrefsKey, "")

        val syncInput: EditText = parent.findViewById(R.id.sync_input)
        syncInput.setText(preferred_sync)
        val tagGroup: ChipGroup = parent.findViewById(R.id.tag_group)
        time_Viewer = parent.findViewById(R.id.time_viewer)
        date_viewer = parent.findViewById(R.id.date_viewer)
        date_viewer.text = _sdfDateTime.format(Date())
        time_Viewer.text = _sdfWatchTime.format(Date())

        val fetchPopulateTagsCallback: OnTagsResultCallback = object : OnTagsResultCallback {
            override fun onTagsResult(
                selectedTags: List<Chip>, unselectedTags: List<Chip>, networkErrorCode: Int
            ) {
                if (networkErrorCode != 200) {
                    Snackbar.make(
                        main_parent_view,
                        "Error while getting the Tags. Offline functionality will be implemented soon",
                        Snackbar.LENGTH_LONG
                    ).setAction("Retry") { view ->
                        fetchTags(preferred_tags, this)
                    }.show()
                } else {
                    selected_tags = selectedTags.toMutableList()
                    unselected_tags = unselectedTags.toMutableList()
                    populateTags(selectedTags, unselectedTags, tagGroup)
                }
                swipeRefreshView.isRefreshing = false
            }
        }
        swipeRefreshView.setOnRefreshListener { ->
            fetchTags(preferred_tags, fetchPopulateTagsCallback)
        }

        fetchTags(preferred_tags, fetchPopulateTagsCallback)

        tagGroup.setOnCheckedStateChangeListener(object : OnCheckedStateChangeListener {
            override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {

                var new_selected_tags: MutableList<Chip> = mutableListOf()
                var new_unselected_tags: MutableList<Chip> = mutableListOf()
                for (chip in selected_tags) {
                    if(chip.id in checkedIds){
                        new_selected_tags.add(chip)
                    } else {
                        new_unselected_tags.add(chip)
                    }
                }
                for (chip in unselected_tags) {
                    if(chip.id in checkedIds){
                        new_selected_tags.add(chip)
                    } else {
                        new_unselected_tags.add(chip)
                    }
                }
                populateTags(new_selected_tags, new_unselected_tags, tagGroup)
                selected_tags = new_selected_tags
                unselected_tags = new_unselected_tags
            }

        })

        applyConfig.setOnClickListener { view ->
            var saved_tags = ""
            for (id in tagGroup.checkedChipIds) {
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
                    Data.Builder().putStringArray("tags", preferred_tags.toTypedArray())
                        .putString("sync", syncString).build()
                ).build()
            WorkManager.getInstance(context).enqueue(downloadWorkRequest)


        }
    }

    private fun fetchTags(
        preferred_tags: List<String>,
        resultCallback: OnTagsResultCallback,
    ) {
        val wallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
        wallpaperApi.getTags().enqueue(object : Callback<TagsResult> {
            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                Log.d("tags_response", response.toString())
                if (response.code() != 200) {
                    resultCallback.onTagsResult(listOf(), listOf(), response.code())
                    return
                }
                val tags = response.body()!!.tags
                val selected_tags: MutableList<Chip> = mutableListOf()
                val unselected_tags: MutableList<Chip> = mutableListOf()
                for (tag in tags) {
                    val view = Chip(
                        requireContext(),
                        null,
                        com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter
                    )
                    view.text = tag
                    view.id = ViewCompat.generateViewId()
                    view.isCheckable = true
                    view.isClickable = true

                    if (tag in preferred_tags) {
                        view.isChecked = true
                        selected_tags.add(view)
                    } else {
                        unselected_tags.add(view)
                    }
                }
                resultCallback.onTagsResult(selected_tags, unselected_tags, 200)
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                resultCallback.onTagsResult(listOf(), listOf(), 500)
            }
        })
    }

    fun populateTags(selectedTags: List<Chip>, unselectedTags: List<Chip>, tagGroup: ChipGroup) {
        tagGroup.removeAllViews()
        for (chip in selectedTags) {
            tagGroup.addView(chip)
        }
        for (chip in unselectedTags) {
            tagGroup.addView(chip)
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


    interface OnTagsResultCallback {
        fun onTagsResult(
            selectedTags: List<Chip>, unselectedTags: List<Chip>, networkErrorCode: Int
        )
    }
}

