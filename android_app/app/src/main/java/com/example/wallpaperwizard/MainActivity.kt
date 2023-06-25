package com.example.wallpaperwizard

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.wallpaperwizard.Fragments.EditFragment
import com.example.wallpaperwizard.Fragments.HomeFragment
import com.example.wallpaperwizard.Fragments.UploadFragment

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById<ViewPager2>(R.id.pager)
        viewPager.isNestedScrollingEnabled = true

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.currentItem = 1

    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        val fragmentClassArray = arrayOf(UploadFragment(), HomeFragment(), EditFragment())
        override fun getItemCount(): Int = fragmentClassArray.size

        override fun createFragment(position: Int): Fragment {
            return fragmentClassArray[position]
        }
    }
}