package com.keren.smartyoga

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionTracker(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("yoga_stats", Context.MODE_PRIVATE)
    
    fun logSessionComplete() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentCount = prefs.getInt(today, 0)
        prefs.edit().putInt(today, currentCount + 1).apply()
        
        // Update total sessions
        val total = prefs.getInt("total_sessions", 0)
        prefs.edit().putInt("total_sessions", total + 1).apply()
    }
    
    fun getTodaySessions(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs.getInt(today, 0)
    }
    
    fun getTotalSessions(): Int {
        return prefs.getInt("total_sessions", 0)
    }
}
