package com.example.wallpaperwizard

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.wallpaperwizard.Fragments.EditFragment
import com.example.wallpaperwizard.Fragments.HomeFragment
import com.example.wallpaperwizard.Fragments.UploadFragment
import com.example.wallpaperwizard.Fragments.UploadFragmentInterface

class MainActivity : AppCompatActivity(), DataPassInterface {
    private lateinit var viewPager: ViewPager2
    private var uploadFragment :Fragment = UploadFragment()
    private var homeFragment :Fragment = HomeFragment()
    private var editFragment :Fragment = EditFragment()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById<ViewPager2>(R.id.pager)
        viewPager.isNestedScrollingEnabled = true

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.currentItem = 1
    }

    override fun passEditWallpaper(wallpaper: MutableList<WallpaperInfoObject>) {
        viewPager.currentItem=0
        (uploadFragment as UploadFragmentInterface).set_wallpaper_info_stack(wallpaper)
        (uploadFragment as UploadFragmentInterface).load_from_wallpaper_info_stack()
    }
    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        val fragmentClassArray = arrayOf(uploadFragment, homeFragment, editFragment)
        override fun getItemCount(): Int = fragmentClassArray.size

        override fun createFragment(position: Int): Fragment {
            return fragmentClassArray[position]
        }
    }
}

interface DataPassInterface {
    fun passEditWallpaper(wallpaper: MutableList<WallpaperInfoObject>)
}