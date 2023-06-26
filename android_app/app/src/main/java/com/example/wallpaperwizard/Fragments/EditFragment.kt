package com.example.wallpaperwizard.Fragments

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
import com.example.wallpaperwizard.Components.TagGroup.OnTagSelectionChangeListener
import com.example.wallpaperwizard.Components.TagGroup.TagGroup
import com.example.wallpaperwizard.DataPassInterface
import com.example.wallpaperwizard.R
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.stream.Collectors


val wallpaperApi = EditFragment.RetrofitHelper.getInstance().create(WallpaperApi::class.java)

class EditFragment : Fragment() {
    var wallpaperList: List<WallpaperInfoObject> = emptyList()
    var shownWallpaper: MutableList<WallpaperInfoObject> = mutableListOf()
    var selectedWallpaper: MutableList<Int> = mutableListOf()
    lateinit var recyclerView: RecyclerView
    lateinit var recyclerAdapter: CustomAdapter
    lateinit var tagsResultCallback: Callback<TagsResult>
    var height: Int = 1920
    var width: Int = 1080

    object RetrofitHelper {
        const val baseUrl = "https://ww.keefer.de"
        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayMetrics = DisplayMetrics()
        activity?.getWindowManager()?.getDefaultDisplay()?.getMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels

        wallpaperApi.getWallpaperList().enqueue(object : Callback<WallpaperListResponse> {

            override fun onFailure(call: Call<WallpaperListResponse>, t: Throwable) {
                Log.d("WallpaperListResponse", "failed to query")
                Log.d("WallpaperListResponse", t.toString())
            }

            override fun onResponse(
                call: Call<WallpaperListResponse>, response: Response<WallpaperListResponse>
            ) {
                wallpaperList = response.body()!!.wallpapers.toList()
                shownWallpaper = wallpaperList.toMutableList()
                recyclerAdapter.dataSet = shownWallpaper
                recyclerAdapter.notifyDataSetChanged()
                Log.d("WallpaperListResponse", response.body()!!.toString())

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
        recyclerView = view.findViewById<RecyclerView>(R.id.edit_fragment_recycler_view)
        recyclerAdapter = CustomAdapter(wallpaperList)
        recyclerView.adapter = recyclerAdapter
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        val editWallpaperFab =
            view.findViewById<FloatingActionButton>(R.id.edit_fragment_edit_wallpaper_fab)

        editWallpaperFab.setOnClickListener {
            if (selectedWallpaper.isEmpty()) {
                Snackbar.make(
                    view,
                    "Select a wallpaper to edit",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                (activity as DataPassInterface).passEditWallpaper(selectedWallpaper.stream().map { shownWallpaper[it] }.collect(Collectors.toList()))
            }
        }

        val tagGroup: TagGroup = view.findViewById(R.id.edit_fragment_tag_layout)



        tagsResultCallback = object : Callback<TagsResult> {
            fun errorHandle() {
                Snackbar.make(
                    view,
                    "Error while getting the Tags. Offline functionality will be implemented soon",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { view ->
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
            val frame_layout: FrameLayout =
                view.findViewById<FrameLayout>(R.id.wallpaper_thumbnail_item_layout)

            frame_layout.minimumHeight = (this@EditFragment.width - 20) / 3
            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            // Get element from your dataset at this position and replace the
            // contents of the view with that element

            if (selectedWallpaper.contains(viewHolder.adapterPosition)) {
                viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
            } else {
                viewHolder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
            }

            viewHolder.imageView.setOnLongClickListener {
                if (!selectedWallpaper.contains(viewHolder.adapterPosition)) {
                    selectedWallpaper.add(viewHolder.adapterPosition)
                    viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
                }
                true
            }

            viewHolder.imageView.setOnClickListener {
                if (selectedWallpaper.contains(viewHolder.adapterPosition)) {
                    selectedWallpaper.remove(viewHolder.adapterPosition)
                    viewHolder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
                } else if (!selectedWallpaper.contains(viewHolder.adapterPosition) && selectedWallpaper.size > 0) {
                    selectedWallpaper.add(viewHolder.adapterPosition)
                    viewHolder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
                }
                true
            }


            wallpaperApi.getThumbnail(dataSet[viewHolder.adapterPosition].name)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>, response: Response<ResponseBody>
                    ) {
                        Log.d("thumbnail_response", dataSet[viewHolder.adapterPosition].name)
                        Log.d("thumbnail_response", response.message())
                        val inputStream = response.body()!!.byteStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        viewHolder.imageView.setImageBitmap(bitmap)
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("thumbnail_response", dataSet[viewHolder.adapterPosition].name)
                    }

                })

        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            if (selectedWallpaper.contains(holder.adapterPosition)) {
                holder.imageView.setColorFilter(Color.argb(120, 200, 200, 255));
            } else {
                holder.imageView.setColorFilter(Color.argb(0, 200, 200, 255));
            }
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = dataSet.size

    }

}