package com.example.wallpaperwizard.Fragments

import RealPathUtil
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.TagsResult
import com.example.wallpaperwizard.WallpaperApi
import com.example.wallpaperwizard.Worker.WallpaperUploaderWorker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis.Axis
import com.ortiz.touchview.OnTouchImageViewListener
import com.ortiz.touchview.TouchImageView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.stream.Collectors


class UploadFragment : Fragment() {
    var upload_file_url = ""
    lateinit var notificationManager: NotificationManager
    lateinit var notiProvider: NotificationProvider
    lateinit var tagsResultCallback: Callback<TagsResult>
    val wallpaperApi = HomeFragment.RetrofitHelper.getInstance().create(WallpaperApi::class.java)


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(parent: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        notiProvider = NotificationProvider(context)
        notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)
        Log.d(
            "worker_list",
            WorkManager.getInstance(context).getWorkInfosByTag("upload").get().toString()
        )

        val main_parent_view: RelativeLayout = parent.findViewById(R.id.upload_parent_view)
        val tagGroup: TagGroup = parent.findViewById(R.id.upload_tag_group)

        val prefs = context.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val preferred_tags = prefs.getString("tags_preferences", "")!!.split(";").stream()
            .filter { str -> str != "" }.collect(
            Collectors.toList()
        )


        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle() {
                Snackbar.make(
                    main_parent_view,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { view ->
                    wallpaperApi.getTags().enqueue(this)
                }.show()
            }

            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {

                if (response.code() == 200) {
                    Log.d("tags_response", response.body()!!.tags.toList().toString())
                    tagGroup.setTags(response.body()!!.tags, preferred_tags.toTypedArray())
                } else {
                    errorHandle()
                }
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                errorHandle()
            }
        }

        wallpaperApi.getTags().enqueue(tagsResultCallback)


        val wallpaper_preview = parent.findViewById<TouchImageView>(R.id.wallpaper_preview)
        wallpaper_preview.setOnTouchImageViewListener(object : OnTouchImageViewListener {
            override fun onMove() {
                requireActivity().findViewById<ViewPager2>(R.id.pager).isUserInputEnabled = false
            }
        })

        val scroll_overlay = parent.findViewById<View>(R.id.scroll_overlay)
        scroll_overlay.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(p0: View?, p1: MotionEvent?): Boolean {
                Log.d("scrolling_behaviour", "overlay is touched")
                requireActivity().findViewById<ViewPager2>(R.id.pager).isUserInputEnabled = true
                return true
            }

        })

        val button_select_wallpaper =
            parent.findViewById<FloatingActionButton>(R.id.select_wallpaper)
        val button_upload_wallpaper =
            parent.findViewById<FloatingActionButton>(R.id.upload_wallpaper)

        val startForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("selection", result.data!!.data.toString())
                val source =
                    ImageDecoder.createSource(context.contentResolver, result.data!!.data!!)
                val bitmap = ImageDecoder.decodeBitmap(source)

                var filePath: String = RealPathUtil.getRealPath(context, result.data!!.data!!)!!
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
            Log.d(
                "selected_tags",
                tagGroup.getSelectedTags().toList().toString()
            )
            Log.d(
                "worker_list",
                WorkManager.getInstance(context).getWorkInfosByTag("upload").get().toString()
            )
            val constraints: Constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val upload_work_request: WorkRequest =
                OneTimeWorkRequestBuilder<WallpaperUploaderWorker>().setInputData(
                    Data.Builder().putString("upload_file_url", upload_file_url)
                        .putString("rect", wallpaper_preview.zoomedRect.toString()).putStringArray("upload_tags", tagGroup.getSelectedTags()).build()
                ).setConstraints(constraints).addTag("upload")
                    .build()
            WorkManager.getInstance(context).enqueue(upload_work_request)

            WorkManager.getInstance(context).getWorkInfoByIdLiveData(upload_work_request.id)
                .observeForever { workInfo ->
                    if (workInfo != null && workInfo.state == WorkInfo.State.ENQUEUED) {
                        val notificationManager =
                            NotificationManagerCompat.from(context)
                        notificationManager.notify(
                            notiProvider.UPLOAD_PENDING_NOTIFICATION_ID,
                            notiProvider.UPLOAD_PENDING_NOTIFICATION
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

    override fun onStop() {
        super.onStop()
    }
}