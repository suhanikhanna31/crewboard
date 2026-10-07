package com.crewboard.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("crewboard", Context.MODE_PRIVATE)

    // 10.0.2.2 = the host machine as seen from the Android emulator
    var baseUrl: String
        get() = prefs.getString("base_url", "http://10.0.2.2:8000")!!
        set(v) = prefs.edit().putString("base_url", v.trim()).apply()
    var username: String
        get() = prefs.getString("username", "ravi")!!
        set(v) = prefs.edit().putString("username", v.trim()).apply()
    var password: String
        get() = prefs.getString("password", "crew123")!!
        set(v) = prefs.edit().putString("password", v).apply()
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()
    var resourceId: Int
        get() = prefs.getInt("resource_id", -1)
        set(v) = prefs.edit().putInt("resource_id", v).apply()
    var onShift: Boolean
        get() = prefs.getBoolean("on_shift", false)
        set(v) = prefs.edit().putBoolean("on_shift", v).apply()
}
