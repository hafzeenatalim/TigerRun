package com.games.tigerrun.dgnvm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {
    
    private lateinit var progressManager: ProgressManager
    private lateinit var serverHelper: ServerHelper
    
    private val levelButtons = mutableListOf<Button>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        serverHelper = ServerHelper(this)
        
        if (serverHelper.checkSavedDataAndOpen()) {
            finish()
            return
        }
        
        initializeMenu()
        
        CoroutineScope(Dispatchers.Main).launch {
            serverHelper.checkServerAndProceed { }
        }
    }
    
    private fun initializeMenu() {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
        setContentView(R.layout.activity_menu)
        hideSystemUI()
        
        progressManager = ProgressManager(this)
        
        levelButtons.apply {
            add(findViewById(R.id.level1Button))
            add(findViewById(R.id.level2Button))
            add(findViewById(R.id.level3Button))
            add(findViewById(R.id.level4Button))
            add(findViewById(R.id.level5Button))
        }
        
        setupLevelButtons()
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (levelButtons.isNotEmpty()) {
            setupLevelButtons()
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post {
            hideSystemUI()
        }
    }
    
    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (e: Exception) {
        }
    }
    
    private fun setupLevelButtons() {
        val unlockedLevel = progressManager.getUnlockedLevel()
        
        levelButtons.forEachIndexed { index, button ->
            val level = index + 1
            
            if (level <= unlockedLevel) {
                button.isEnabled = true
                button.alpha = 1.0f
                val levelText = when(level) {
                    1 -> getString(R.string.level_1)
                    2 -> getString(R.string.level_2)
                    3 -> getString(R.string.level_3)
                    4 -> getString(R.string.level_4)
                    5 -> getString(R.string.level_5)
                    else -> "Level $level"
                }
                button.text = levelText
                button.setOnClickListener {
                    startGame(level)
                }
            } else {
                button.isEnabled = false
                button.alpha = 0.5f
                button.text = getString(R.string.locked)
            }
        }
    }
    
    private fun startGame(level: Int) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("LEVEL", level)
        startActivity(intent)
    }
}



