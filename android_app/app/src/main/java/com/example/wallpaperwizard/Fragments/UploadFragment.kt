package com.example.wallpaperwizard.Fragments

import RealPathUtil
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.NotificationProvider
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.RetrofitHelper
import com.example.wallpaperwizard.TagsResult
import com.example.wallpaperwizard.WallpaperApi
import com.example.wallpaperwizard.WallpaperInfoObject
import com.example.wallpaperwizard.Worker.WallpaperUploaderWorker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.ortiz.touchview.OnTouchImageViewListener
import com.ortiz.touchview.TouchImageView
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.stream.Collectors


class UploadFragment : Fragment(), UploadFragmentInterface {
    var uploadFileUrl = ""
    lateinit var notificationManager: NotificationManager
    private lateinit var notiProvider: NotificationProvider
    lateinit var tagsResultCallback: Callback<TagsResult>
    val wallpaperApi: WallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
    lateinit var currentBitmap: Bitmap
    lateinit var wallpaperPreview: TouchImageView
    private val wallpaperStack = mutableListOf<Any>()
    var updatingWallpaper = false
    var currentWallpaperName = ""
    lateinit var tagGroup: TagGroup
    lateinit var mainParentView: ConstraintLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(parent: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()

        //Notification Setup
        notiProvider = NotificationProvider(context)
        notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        )!!
        notiProvider.createNotificationChannels(notificationManager)


        Log.d(
            "worker_list",
            WorkManager.getInstance(context).getWorkInfosByTag("upload").get().toString()
        )

        mainParentView = parent.findViewById(R.id.upload_parent_view)

        val swipeRefreshLayout =
            parent.findViewById<SwipeRefreshLayout>(R.id.upload_swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            wallpaperApi.getTags().enqueue(tagsResultCallback)
        }

        tagGroup = parent.findViewById(R.id.upload_tag_group)
        val prefs = context.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        val preferredTags = prefs.getString("tags_preferences", "")!!.split(";").stream()
            .filter { str -> str != "" }.collect(
                Collectors.toList()
            )
        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle() {
                swipeRefreshLayout.isRefreshing = false
                Snackbar.make(
                    mainParentView,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") {
                    wallpaperApi.getTags().enqueue(this)
                }.show()
            }

            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                if (response.code() == 200) {
                    swipeRefreshLayout.isRefreshing = false
                    Log.d("tags_response", response.body()!!.tags.toList().toString())
                    tagGroup.setTags(response.body()!!.tags, preferredTags.toTypedArray())
                } else {
                    errorHandle()
                }
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                errorHandle()
            }
        }
        wallpaperApi.getTags().enqueue(tagsResultCallback)


        val clearWallpaperFab =
            parent.findViewById<FloatingActionButton>(R.id.upload_fragment_clear_wallpaper)
        clearWallpaperFab.setOnClickListener {
            wallpaperPreview.x = 0f
            wallpaperPreview.animate().alpha(0f).x(-800F).setDuration(500).withEndAction {
                wallpaperPreview.visibility = GONE
                wallpaperPreview.translationY = 0f
                wallpaperPreview.x = 0f
                if (wallpaperStack.isNotEmpty()) {
                    loadFromStack()
                }
            }
        }




        wallpaperPreview = parent.findViewById<TouchImageView>(R.id.wallpaper_preview)
        wallpaperPreview.visibility = GONE
        wallpaperPreview.setOnTouchImageViewListener(object : OnTouchImageViewListener {
            override fun onMove() {
                requireActivity().findViewById<ViewPager2>(R.id.pager).isUserInputEnabled = false
            }
        })

        val scrollOverlay = parent.findViewById<View>(R.id.scroll_overlay)
        scrollOverlay.setOnTouchListener { p0, _ ->
            p0.performClick()
            Log.d("scrolling_behaviour", "overlay is touched")
            requireActivity().findViewById<ViewPager2>(R.id.pager).isUserInputEnabled = true
            true
        }

        val buttonSelectWallpaper = parent.findViewById<FloatingActionButton>(R.id.select_wallpaper)
        val startForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (result.data?.clipData?.itemCount != null) {
                    for (i in 0 until result.data?.clipData!!.itemCount) {
                        Log.d("selection", result.data!!.clipData!!.getItemAt(i).uri.toString())
                        wallpaperStack.add(result.data!!.clipData!!.getItemAt(i).uri)
                    }
                    loadFromStack()
                } else {
                    Log.d("selection", result.data?.data.toString())
                    wallpaperStack.add(result.data!!.data!!)
                    loadFromStack()
                }
            }
        }
        buttonSelectWallpaper.setOnClickListener {
            if (checkSelfPermission(
                    context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent()
                intent.type = "image/*"
                intent.action = Intent.ACTION_GET_CONTENT
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startForResult.launch(intent)
            } else {
                Snackbar.make(
                    mainParentView,
                    "Please allow this app to access your storage",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        val buttonUploadWallpaper = parent.findViewById<FloatingActionButton>(R.id.upload_wallpaper)


        buttonUploadWallpaper.setOnClickListener {
            Log.d(
                "selected_tags", tagGroup.getSelectedTags().toList().toString()
            )
            if (!this::currentBitmap.isInitialized) {
                Snackbar.make(
                    mainParentView, "Please select a wallpaper", Snackbar.LENGTH_LONG
                ).show()
            } else {
                val constraints: Constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

                val crop =
                    "${wallpaperPreview.zoomedRect.left * currentBitmap.width},${wallpaperPreview.zoomedRect.top * currentBitmap.height},${wallpaperPreview.zoomedRect.right * currentBitmap.width},${wallpaperPreview.zoomedRect.bottom * currentBitmap.height}"
                val uploadWorkRequest: WorkRequest =
                    OneTimeWorkRequestBuilder<WallpaperUploaderWorker>().setInputData(
                        Data.Builder().putString("upload_file_url", uploadFileUrl)
                            .putString("rect", wallpaperPreview.zoomedRect.toString())
                            .putBoolean("updating", updatingWallpaper)
                            .putString("name", currentWallpaperName)
                            .putStringArray("upload_tags", tagGroup.getSelectedTags())
                            .putString("crop_preference", crop).build()
                    ).setConstraints(constraints).addTag("upload").build()
                WorkManager.getInstance(context).enqueue(uploadWorkRequest)
                wallpaperPreview.animate().alpha(0f).translationY(-800F).setDuration(500)
                    .withEndAction {
                        wallpaperPreview.visibility = GONE
                        wallpaperPreview.translationY = 0f
                    }
                updatingWallpaper = false

                Snackbar.make(
                    mainParentView, "Uploading your wallpaper", Snackbar.LENGTH_LONG
                ).show()


                WorkManager.getInstance(context).getWorkInfoByIdLiveData(uploadWorkRequest.id)
                    .observeForever { workInfo ->
                        if (workInfo != null && workInfo.state == WorkInfo.State.ENQUEUED) {
                            val notificationManager = NotificationManagerCompat.from(context)
                            notificationManager.notify(
                                notiProvider.UPLOAD_PENDING_NOTIFICATION_ID,
                                notiProvider.UPLOAD_PENDING_NOTIFICATION
                            )
                        }
                        if (workInfo != null && workInfo.state.isFinished) {
                            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                Snackbar.make(
                                    mainParentView,
                                    "Successfully uploaded your wallpaper",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                notificationManager.notify(
                                    1, notiProvider.UPLOAD_SUCCESS_NOTIFICATION
                                )
                                notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
                            } else {
                                Snackbar.make(
                                    mainParentView,
                                    "There was a problem while uploading",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                notificationManager.notify(
                                    1, notiProvider.UPLOAD_FAILURE_NOTIFICATION
                                )
                                notificationManager.cancel(notiProvider.UPLOAD_RUNNING_NOTIFICATION_ID)
                            }
                        }
                    }
                loadFromStack()
            }
        }
    }

    override fun loadFromStack() {
        if (wallpaperStack.size == 0) return
        when (val currentWallpaperObject = wallpaperStack.removeAt(wallpaperStack.size - 1)) {
            is Uri -> {
                val currentUri: Uri = currentWallpaperObject
                val source = requireContext().let {
                    ImageDecoder.createSource(
                        it.contentResolver, currentUri
                    )
                }
                currentBitmap = ImageDecoder.decodeBitmap(source)
                uploadFileUrl = RealPathUtil.getRealPath(requireContext(), currentUri)!!
                animateWallpaperLoading()
            }

            is WallpaperInfoObject -> {
                Log.d("currentWallpaperObject", currentWallpaperObject.toString())
                val currentWallpaperInfoObject: WallpaperInfoObject = currentWallpaperObject
                tagGroup.setTags(tagGroup.tags, currentWallpaperInfoObject.tags)

                //See if the bitmap is already stored on the device
                val imagePath: String =
                    requireContext().filesDir.absolutePath + "/" + currentWallpaperInfoObject.name
                val imageFile = File(imagePath)

                if (imageFile.exists()) {
                    currentBitmap = BitmapFactory.decodeFile(imagePath)
                    animateWallpaperLoading()
                } else {
                    // The file does not exist
                    wallpaperApi.getWallpaperByName(currentWallpaperInfoObject.name)
                        .enqueue(object : Callback<ResponseBody> {

                            fun errorHandle() {
                                Snackbar.make(
                                    mainParentView,
                                    "Error while getting the Wallpaper",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                loadFromStack()
                            }

                            override fun onResponse(
                                p0: Call<ResponseBody>, p1: Response<ResponseBody>
                            ) {
                                if (p1.code() == 200) {
                                    val inputStream = p1.body()!!.byteStream()
                                    currentBitmap = BitmapFactory.decodeStream(inputStream)
                                    inputStream.close()
                                    animateWallpaperLoading()
                                } else {
                                    errorHandle()
                                }
                            }

                            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                errorHandle()
                            }
                        })
                }
            }

            else -> {
                return
            }
        }
    }

    private fun animateWallpaperLoading() {
        wallpaperPreview.alpha = 0f
        wallpaperPreview.visibility = VISIBLE
        wallpaperPreview.y = 800f
        wallpaperPreview.x = 0f

        wallpaperPreview.resetZoomAnimated()

        wallpaperPreview.setImageBitmap(currentBitmap)
        wallpaperPreview.animate().alpha(1f).y(0F).setDuration(500).withEndAction {
            wallpaperPreview.alpha = 1f
            wallpaperPreview.y = 0f
        }
    }

    override fun addToWallpaperStack(wallpaper_info_stack: List<WallpaperInfoObject>) {
        this.wallpaperStack.addAll(wallpaper_info_stack.toMutableList())
    }
}

interface UploadFragmentInterface {
    fun addToWallpaperStack(wallpaper_info_stack: List<WallpaperInfoObject>)
    fun loadFromStack()
}