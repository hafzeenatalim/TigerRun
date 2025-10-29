package com.games.tigerrun.dgnvm

import android.content.Context
import android.content.SharedPreferences

class DataManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "AppData",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_CONTENT_PATH = "content_path"
    }
    
    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }
    
    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun saveContentPath(path: String) {
        prefs.edit().putString(KEY_CONTENT_PATH, path).apply()
    }
    
    fun getContentPath(): String? {
        return prefs.getString(KEY_CONTENT_PATH, null)
    }
    
    fun hasAccessToken(): Boolean {
        return !getAccessToken().isNullOrEmpty()
    }
}

