package com.example.wallpaperwizard.Fragments

import RealPathUtil
import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
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
    lateinit var current_bitmap : Bitmap
    lateinit var wallpaper_preview: TouchImageView


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

        val main_parent_view: ConstraintLayout = parent.findViewById(R.id.upload_parent_view)
        val tagGroup: TagGroup = parent.findViewById(R.id.upload_tag_group)
        val swipeRefreshLayout = parent.findViewById<SwipeRefreshLayout>(R.id.upload_swipe_refresh_layout)

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
        swipeRefreshLayout.setOnRefreshListener {
            wallpaperApi.getTags().enqueue(tagsResultCallback)
            swipeRefreshLayout.isRefreshing = false
        }


        wallpaper_preview = parent.findViewById<TouchImageView>(R.id.wallpaper_preview)
        wallpaper_preview.visibility = GONE
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
                current_bitmap = ImageDecoder.decodeBitmap(source)

                var filePath: String = RealPathUtil.getRealPath(context, result.data!!.data!!)!!
                upload_file_url = filePath
                wallpaper_preview.alpha = 0f
                wallpaper_preview.setVisibility(View.VISIBLE)
                wallpaper_preview.translationY = 800f

                wallpaper_preview.resetZoomAnimated()

                wallpaper_preview.setImageBitmap(current_bitmap)
                wallpaper_preview.animate().alpha(1f).translationY(0F).setDuration(500).withEndAction {
                    wallpaper_preview.alpha = 1f
                    wallpaper_preview.translationY = 0f
                }


            }
        }
        button_select_wallpaper.setOnClickListener { view ->
            if (checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent()
                intent.type = "image/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startForResult.launch(intent)
            } else {
                Snackbar.make(
                    main_parent_view,
                    "Please allow this app to access your storage",
                    Snackbar.LENGTH_LONG
                ).show()
            }


        }

        button_upload_wallpaper.setOnClickListener { view ->
            Log.d(
                "selected_tags",
                tagGroup.getSelectedTags().toList().toString()
            )
            if (!this::current_bitmap.isInitialized){
                Snackbar.make(
                    main_parent_view,
                    "Please select a wallpaper",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                val constraints: Constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

                val crop = "${wallpaper_preview.zoomedRect.left * current_bitmap.width},${wallpaper_preview.zoomedRect.top* current_bitmap.height},${wallpaper_preview.zoomedRect.right * current_bitmap.width},${wallpaper_preview.zoomedRect.bottom * current_bitmap.height}"
                val upload_work_request: WorkRequest =
                    OneTimeWorkRequestBuilder<WallpaperUploaderWorker>().setInputData(
                        Data.Builder().putString("upload_file_url", upload_file_url)
                            .putString("rect", wallpaper_preview.zoomedRect.toString()).putStringArray("upload_tags", tagGroup.getSelectedTags()).putString("crop_preference", crop).build()
                    ).setConstraints(constraints).addTag("upload")
                        .build()
                WorkManager.getInstance(context).enqueue(upload_work_request)
                wallpaper_preview.animate().alpha(0f).translationY(-800F).setDuration(500).withEndAction {
                    wallpaper_preview.visibility = GONE
                    wallpaper_preview.translationY = 0f
                }

                Snackbar.make(
                    main_parent_view,
                    "Uploading your wallpaper",
                    Snackbar.LENGTH_LONG
                ).show()

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
                                Snackbar.make(
                                    main_parent_view,
                                    "Sucessfully uploaded your wallpaper",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                notificationManager.notify(1, notiProvider.UPLOAD_SUCCESS_NOTIFICATION)
                                notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
                            } else {
                                Snackbar.make(
                                    main_parent_view,
                                    "There was a problem while uploading",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                notificationManager.notify(1, notiProvider.UPLOAD_FAILURE_NOTIFICATION)
                                notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
                            }
                        }
                    }
            }

        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
    }
}
