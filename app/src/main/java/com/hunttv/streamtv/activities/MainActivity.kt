package com.hunttv.streamtv.activities

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hunttv.streamtv.R
import com.hunttv.streamtv.fragments.HomeFragment
import com.hunttv.streamtv.fragments.PlaylistsFragment
import com.hunttv.streamtv.network.RetrofitClient
import com.hunttv.streamtv.utils.AppConfig
import com.hunttv.streamtv.utils.BannerManager
import com.onesignal.OneSignal
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeOneSignal()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(HomeFragment()); true }
                R.id.nav_playlists -> { loadFragment(PlaylistsFragment()); true }
                else -> false
            }
        }

        lifecycleScope.launch {
            try {
                val bannerConfig = RetrofitClient.api.getBannerConfig(AppConfig.API_KEY)
                if (bannerConfig.banner.enabled) {
                    if (BannerManager.shouldShowBanner(this@MainActivity, bannerConfig.banner.show_per_day)) {
                        showBannerDialog(bannerConfig.banner.html_url)
                        BannerManager.markBannerShown(this@MainActivity)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun initializeOneSignal() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(AppConfig.ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications()
    }

    private fun showBannerDialog(htmlUrl: String) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_banner, null)
        dialog.setContentView(view)
        val webView = view.findViewById<WebView>(R.id.webview_banner)
        val closeButton = view.findViewById<ImageButton>(R.id.btn_close)
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.loadUrl(htmlUrl)
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}
