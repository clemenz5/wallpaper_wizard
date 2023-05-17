package com.example.wallpaperwizard.Fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wallpaperwizard.R
import com.example.wallpaperwizard.WallpaperApi
import com.example.wallpaperwizard.WallpaperInfoObject
import com.example.wallpaperwizard.WallpaperListResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val wallpaperApi = EditFragment.RetrofitHelper.getInstance().create(WallpaperApi::class.java)

class EditFragment : Fragment() {
    var wallpaperList: Array<WallpaperInfoObject> = emptyArray()
    lateinit var recyclerView: RecyclerView
    lateinit var recyclerAdapter: CustomAdapter

    object RetrofitHelper {
        val baseUrl = "https://ww.keefer.de"
        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wallpaperApi.getWallpaperList().enqueue(object : Callback<WallpaperListResponse> {

            override fun onFailure(call: Call<WallpaperListResponse>, t: Throwable) {
                Log.d("WallpaperListResponse", "failed to query")
                Log.d("WallpaperListResponse", t.toString())
            }

            override fun onResponse(
                call: Call<WallpaperListResponse>,
                response: Response<WallpaperListResponse>
            ) {
                wallpaperList = response.body()!!.wallpapers
                recyclerAdapter.dataSet = wallpaperList
                recyclerAdapter.notifyDataSetChanged()
                Log.d("WallpaperListResponse", response.body()!!.toString())

            }

        })


    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById<RecyclerView>(R.id.edit_fragment_recycler_view)
        recyclerAdapter = CustomAdapter(wallpaperList)
        recyclerView.adapter = recyclerAdapter
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    class CustomAdapter(var dataSet: Array<WallpaperInfoObject>) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        /**
         * Provide a reference to the type of views that you are using
         * (custom ViewHolder)
         */
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            Log.d("recycler_bind", "view binding")
            // Get element from your dataset at this position and replace the
            // contents of the view with that element
            wallpaperApi.getThumbnail(dataSet[position].name)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        Log.d("thumbnail_response", dataSet[position].name)
                        Log.d("thumbnail_response", response.message())
                        val inputStream = response.body()!!.byteStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        viewHolder.imageView.setImageBitmap(bitmap)
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("thumbnail_response", dataSet[position].name)
                    }

                })
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = dataSet.size

    }

}