package com.games.tigerrun.dgnvm

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {
    
    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var speedText: TextView
    private lateinit var pauseButton: android.widget.Button
    private lateinit var progressManager: ProgressManager
    
    private var currentLevel: Int = 1
    private val unlockedDuringGame = mutableSetOf<Int>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Устанавливаем ориентацию без задержки (без reverse)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
        
        setContentView(R.layout.activity_game)
        
        hideSystemUI()
        
        progressManager = ProgressManager(this)
        
        // Получаем уровень из intent
        currentLevel = intent.getIntExtra("LEVEL", 1)
        
        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreText)
        levelText = findViewById(R.id.levelText)
        speedText = findViewById(R.id.speedText)
        pauseButton = findViewById(R.id.pauseButton)
        
        levelText.text = getString(R.string.level_1).replace("1", currentLevel.toString())
        
        // Обработчик кнопки паузы
        pauseButton.setOnClickListener {
            showPauseDialog()
        }
        
        // Устанавливаем callback для обновления очков
        gameView.setScoreUpdateListener { score ->
            runOnUiThread {
                scoreText.text = getString(R.string.score, score)
            }
        }
        
        // Устанавливаем callback для смены уровня
        gameView.setLevelChangeListener { level ->
            runOnUiThread {
                levelText.text = getString(R.string.level_1).replace("1", level.toString())
            }
        }
        
        // Устанавливаем callback для обновления скорости
        gameView.setSpeedUpdateListener { speed ->
            runOnUiThread {
                speedText.text = "Speed: ${speed.toInt()}"
            }
        }
        
        // Устанавливаем callback для game over
        gameView.setGameOverListener { finalScore, maxLevel ->
            runOnUiThread {
                showGameOverDialog(finalScore, maxLevel)
            }
        }
        
        // Очищаем список разблокированных уровней при новой игре
        unlockedDuringGame.clear()
        
        // Запускаем игру с выбранным уровнем
        gameView.startGame(currentLevel)
        
        // Обработка кнопки "Назад"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }
    
    private fun showGameOverDialog(finalScore: Int, maxReachedLevel: Int) {
        // Сохраняем результат
        progressManager.saveBestScore(currentLevel, finalScore)
        
        // Разблокируем все уровни до максимального достигнутого (включительно)
        var newLevelsUnlocked = false
        for (level in (currentLevel + 1)..maxReachedLevel) {
            if (level <= 5 && !progressManager.isLevelUnlocked(level)) {
                progressManager.unlockLevel(level)
                newLevelsUnlocked = true
            }
        }
        
        // Показываем уведомление о разблокировке
        if (newLevelsUnlocked) {
            val unlockedCount = maxReachedLevel - currentLevel
            val message = if (unlockedCount == 1) {
                "🎉 Level ${currentLevel + 1} Unlocked!"
            } else {
                "🎉 Unlocked $unlockedCount Levels!"
            }
            android.widget.Toast.makeText(
                this,
                message,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_over))
            .setMessage(getString(R.string.final_score, finalScore))
            .setPositiveButton(getString(R.string.restart)) { _, _ ->
                gameView.startGame(currentLevel)
            }
            .setNegativeButton(getString(R.string.menu)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (gameView.isGameStarted()) {
            gameView.resumeGame()
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
        // Мгновенно применяем полноэкранный режим без задержки
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
            
            // Разрешаем контент под вырезом экрана (для Android P+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при скрытии UI
        }
    }
    
    private fun handleBackPress() {
        showPauseDialog()
    }
    
    fun unlockLevelDuringGame(level: Int) {
        if (level <= 5 && !progressManager.isLevelUnlocked(level) && !unlockedDuringGame.contains(level)) {
            progressManager.unlockLevel(level)
            unlockedDuringGame.add(level)
            
            android.widget.Toast.makeText(
                this,
                "🎉 Level $level Unlocked!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun showPauseDialog() {
        // Ставим игру на паузу
        gameView.pauseGame()
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pause))
            .setMessage(getString(R.string.pause_message))
            .setPositiveButton(getString(R.string.continue_game)) { _, _ ->
                gameView.resumeGame()
            }
            .setNegativeButton(getString(R.string.exit_to_menu)) { _, _ ->
                finish()
            }
            .setNeutralButton(getString(R.string.restart)) { _, _ ->
                gameView.startGame(currentLevel)
            }
            .setCancelable(false)
            .show()
    }
}



