package com.example.wallpaperwizard.Fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.wallpaperwizard.Components.TagGroup.OnTagSelectionChangeListener
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.DataPassInterface
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.RetrofitHelper
import com.example.wallpaperwizard.TagsResult
import com.example.wallpaperwizard.WallpaperApi
import com.example.wallpaperwizard.WallpaperInfoObject
import com.example.wallpaperwizard.WallpaperListResponse
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.stream.Collectors


class EditFragment : Fragment() {
    var wallpaperList: List<WallpaperInfoObject> = emptyList()
    var shownWallpaper: MutableList<WallpaperInfoObject> = mutableListOf()
    var selectedWallpaper: MutableList<WallpaperInfoObject> = mutableListOf()
    lateinit var recyclerView: RecyclerView
    lateinit var recyclerAdapter: CustomAdapter
    lateinit var tagsResultCallback: Callback<TagsResult>
    var height: Int = 1920
    var width: Int = 1080
    val wallpaperApi = RetrofitHelper.getInstance().create(WallpaperApi::class.java)
    lateinit var mainParentView: View
    lateinit var onRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayMetrics = DisplayMetrics()
        activity?.getWindowManager()?.getDefaultDisplay()?.getMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels
        queryWallpaperList()


    }

    private fun queryWallpaperList() {
        wallpaperApi.getWallpaperList().enqueue(object : Callback<WallpaperListResponse> {
            fun errorHandle() {
                onRefreshLayout.isRefreshing = false
                Log.d("WallpaperListResponse", "failed to query")
                Snackbar.make(
                    mainParentView, "Error while getting the Wallpaper List", Snackbar.LENGTH_LONG
                ).setAction("Retry") {
                    wallpaperApi.getWallpaperList().enqueue(this)
                }.show()
            }

            override fun onFailure(call: Call<WallpaperListResponse>, t: Throwable) {
                errorHandle()
            }

            override fun onResponse(
                call: Call<WallpaperListResponse>, response: Response<WallpaperListResponse>
            ) {
                if (response.code() == 200) {
                    onRefreshLayout.isRefreshing = false
                    wallpaperList = response.body()!!.wallpapers.toList()
                    shownWallpaper = wallpaperList.toMutableList()
                    recyclerAdapter.dataSet = shownWallpaper
                    recyclerAdapter.notifyDataSetChanged()
                    Log.d("WallpaperListResponse", response.body()!!.toString())
                } else {
                    errorHandle()
                }


            }

        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainParentView = view
        recyclerView = view.findViewById(R.id.edit_fragment_recycler_view)
        recyclerAdapter = CustomAdapter(wallpaperList)
        recyclerView.adapter = recyclerAdapter
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        onRefreshLayout = view.findViewById(R.id.edit_fragment_swipe_refresh_layout)

        onRefreshLayout.setOnRefreshListener {
            queryWallpaperList()

        }

        val editWallpaperFab =
            view.findViewById<FloatingActionButton>(R.id.edit_fragment_edit_wallpaper_fab)

        editWallpaperFab.setOnClickListener {
            if (selectedWallpaper.isEmpty()) {
                Snackbar.make(
                    view, "Select a wallpaper to edit", Snackbar.LENGTH_LONG
                ).show()
            } else {
                (activity as DataPassInterface).passEditWallpaper(selectedWallpaper)
            }
        }

        val deleteWallpaperFab =
            view.findViewById<FloatingActionButton>(R.id.edit_fragment_delete_wallpaper)

        deleteWallpaperFab.setOnClickListener {
            if (selectedWallpaper.isEmpty()) {
                Snackbar.make(
                    view, "Select a wallpaper to delete", Snackbar.LENGTH_LONG
                ).show()
            } else {
                selectedWallpaper.stream().forEach {
                    wallpaperApi.deleteWallpaper(it.name).enqueue(object : Callback<ResponseBody> {
                        fun errorHandle() {
                            Snackbar.make(
                                mainParentView, "Error deleting you wallpaper", Snackbar.LENGTH_LONG
                            ).show()
                        }

                        override fun onResponse(
                            call: Call<ResponseBody>, response: Response<ResponseBody>
                        ) {
                            if (response.code() == 200) {
                                queryWallpaperList()
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
        }

        val tagGroup: TagGroup = view.findViewById(R.id.edit_fragment_tag_layout)

        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle() {
                Snackbar.make(
                    view,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") {
                    wallpaperApi.getTags().enqueue(this)
                }.show()
            }

            override fun onResponse(call: Call<TagsResult>, response: Response<TagsResult>) {
                if (response.code() == 200) {
                    Log.d("tags_response", response.body()!!.tags.toList().toString())
                    tagGroup.setTags(response.body()!!.tags, emptyArray())
                } else {
                    errorHandle()
                }
            }

            override fun onFailure(call: Call<TagsResult>, t: Throwable) {
                errorHandle()
            }
        }
        wallpaperApi.getTags().enqueue(tagsResultCallback)
        tagGroup.addOnSelectionChangeListener(object : OnTagSelectionChangeListener {
            override fun selectionChanged(
                selectedChips: MutableList<Chip>, unselectedChips: MutableList<Chip>
            ) {
                val selectedTags =
                    selectedChips.stream().map { chip -> chip.text }.map { it.toString() }
                        .collect(Collectors.toList())
                filterOnTags(selectedTags.toList())
            }
        })
    }

    fun filterOnTags(selectedTags: List<String>) {
        if (selectedTags.isEmpty()) {
            shownWallpaper = wallpaperList.toMutableList()
        } else {
            shownWallpaper = mutableListOf()
            for (tag in selectedTags) {
                for (wallpaper in wallpaperList) {
                    if (wallpaper.tags.contains(tag) && !shownWallpaper.contains(wallpaper)) {
                        shownWallpaper.add(wallpaper)
                    }
                }
            }
        }

        recyclerAdapter.dataSet = shownWallpaper
        recyclerAdapter.notifyDataSetChanged()
    }

    inner class CustomAdapter(var dataSet: List<WallpaperInfoObject>) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        /**
         * Provide a reference to the type of views that you are using
         * (custom ViewHolder)
         */
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView
            lateinit var wallpaperInfoObject: WallpaperInfoObject

            init {
                // Define click listener for the ViewHolder's View
                imageView = view.findViewById(R.id.wallpaper_thumbnail_item_image_view)
            }
        }

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view, which defines the UI of the list item
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.wallpaper_thumbnail_item, viewGroup, false)
            val frameLayout: FrameLayout =
                view.findViewById<FrameLayout>(R.id.wallpaper_thumbnail_item_layout)

            frameLayout.minimumHeight = (this@EditFragment.width - 20) / 3
            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            // Get element from your dataset at this position and replace the
            // contents of the view with that element
            viewHolder.wallpaperInfoObject = shownWallpaper[position]

            if (selectedWallpaper.contains(viewHolder.wallpaperInfoObject)) {
                viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
            } else {
                viewHolder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
            }

            viewHolder.imageView.setOnLongClickListener {
                if (!selectedWallpaper.contains(viewHolder.wallpaperInfoObject)) {
                    selectedWallpaper.add(viewHolder.wallpaperInfoObject)
                    viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
                }
                true
            }

            viewHolder.imageView.setOnClickListener {
                if (selectedWallpaper.contains(viewHolder.wallpaperInfoObject)) {
                    selectedWallpaper.remove(viewHolder.wallpaperInfoObject)
                    viewHolder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
                } else if (!selectedWallpaper.contains(viewHolder.wallpaperInfoObject) && selectedWallpaper.size > 0) {
                    selectedWallpaper.add(viewHolder.wallpaperInfoObject)
                    viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
                }
                true
            }

            //See if the bitmap is already stored on the device
            val imagePath: String =
                requireContext().filesDir.absolutePath + "/" + viewHolder.wallpaperInfoObject.name
            val imageFile = File(imagePath)

            if (imageFile.exists()) {
                viewHolder.imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath))
            } else {
                wallpaperApi.getThumbnail(viewHolder.wallpaperInfoObject.name)
                    .enqueue(object : Callback<ResponseBody> {

                        fun errorHandle() {
                            Log.d("thumbnail_response", viewHolder.wallpaperInfoObject.name)
                            Snackbar.make(
                                mainParentView,
                                "Error while getting the Thumbnail",
                                Snackbar.LENGTH_LONG
                            ).setAction("Retry") {
                                wallpaperApi.getThumbnail(viewHolder.wallpaperInfoObject.name)
                                    .enqueue(this)
                            }.show()
                        }

                        override fun onResponse(
                            call: Call<ResponseBody>, response: Response<ResponseBody>
                        ) {
                            if (response.code() == 200) {
                                Log.d("thumbnail_response", viewHolder.wallpaperInfoObject.name)
                                Log.d("thumbnail_response", response.message())
                                val inputStream = response.body()!!.byteStream()
                                val bitmap = BitmapFactory.decodeStream(inputStream)

                                //Save image to local storage
                                try {
                                    FileOutputStream(File(imagePath)).use { outputStream ->
                                        bitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            100,
                                            outputStream
                                        )
                                        outputStream.flush()
                                    }
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }

                                inputStream.close()
                                viewHolder.imageView.setImageBitmap(bitmap)
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

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            if (selectedWallpaper.contains(holder.wallpaperInfoObject)) {
                holder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
            } else {
                holder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
            }
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = dataSet.size
    }
}