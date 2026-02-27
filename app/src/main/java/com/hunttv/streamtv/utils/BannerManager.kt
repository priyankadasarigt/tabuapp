package com.hunttv.streamtv.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object BannerManager {
    
    private const val PREFS_NAME = "banner_prefs"
    private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
    private const val KEY_SHOWN_COUNT_TODAY = "shown_count_today"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    fun shouldShowBanner(context: Context, maxPerDay: Int): Boolean {
        if (maxPerDay == 0) return true
        
        val prefs = getPrefs(context)
        val today = getCurrentDate()
        val lastShownDate = prefs.getString(KEY_LAST_SHOWN_DATE, "")
        
        if (lastShownDate != today) {
            prefs.edit()
                .putString(KEY_LAST_SHOWN_DATE, today)
                .putInt(KEY_SHOWN_COUNT_TODAY, 0)
                .apply()
            return true
        }
        
        val shownCount = prefs.getInt(KEY_SHOWN_COUNT_TODAY, 0)
        return shownCount < maxPerDay
    }
    
    fun markBannerShown(context: Context) {
        val prefs = getPrefs(context)
        val today = getCurrentDate()
        val lastShownDate = prefs.getString(KEY_LAST_SHOWN_DATE, "")
        
        if (lastShownDate != today) {
            prefs.edit()
                .putString(KEY_LAST_SHOWN_DATE, today)
                .putInt(KEY_SHOWN_COUNT_TODAY, 1)
                .apply()
        } else {
            val currentCount = prefs.getInt(KEY_SHOWN_COUNT_TODAY, 0)
            prefs.edit()
                .putInt(KEY_SHOWN_COUNT_TODAY, currentCount + 1)
                .apply()
        }
    }
}
