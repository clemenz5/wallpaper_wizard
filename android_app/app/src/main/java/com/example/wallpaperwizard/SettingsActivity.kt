package com.example.wallpaperwizard
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.wallpaperwizard.Worker.WallpaperChangerWorker
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        val settings =
            PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
        val prefs = this.getSharedPreferences(
            "wallpaper_wizard.preferences", Context.MODE_PRIVATE
        )
        settings.registerOnSharedPreferenceChangeListener { p0, p1 ->
            if (settings.getBoolean(p1, false)) {
                val constraints: Constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val downloadWorkRequest: PeriodicWorkRequest =
                    PeriodicWorkRequestBuilder<WallpaperChangerWorker>(24, TimeUnit.HOURS)
                        .addTag("periodic_updater").setInitialDelay(0, TimeUnit.MINUTES)
                        .setInputData(Data.Builder().putStringArray("download_tags", prefs.getString("tags_preferences", "")!!.split(";").stream().filter { str -> str != "" }.toArray { size -> arrayOfNulls<String>(size) }).putString("sync", prefs.getString("sync_preferences", "")).build())
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                        .build()
                WorkManager.getInstance(this).enqueueUniquePeriodicWork("periodic_updater", ExistingPeriodicWorkPolicy.REPLACE, downloadWorkRequest)
            } else if (!settings.getBoolean(p1, false)) {
                WorkManager.getInstance(this).cancelAllWorkByTag("periodic_updater")
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}