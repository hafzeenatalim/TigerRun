package com.games.tigerrun.dgnvm

import android.content.Context
import android.content.SharedPreferences

class ProgressManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "TigerRunProgress",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_UNLOCKED_LEVEL = "unlocked_level"
        private const val KEY_BEST_SCORE = "best_score_"
    }
    
    // Получить максимальный разблокированный уровень
    fun getUnlockedLevel(): Int {
        return prefs.getInt(KEY_UNLOCKED_LEVEL, 1)
    }
    
    // Разблокировать уровень
    fun unlockLevel(level: Int) {
        val currentUnlocked = getUnlockedLevel()
        if (level > currentUnlocked) {
            prefs.edit().putInt(KEY_UNLOCKED_LEVEL, level).apply()
        }
    }
    
    // Проверить, разблокирован ли уровень
    fun isLevelUnlocked(level: Int): Boolean {
        return level <= getUnlockedLevel()
    }
    
    // Сохранить лучший результат для уровня
    fun saveBestScore(level: Int, score: Int) {
        val currentBest = getBestScore(level)
        if (score > currentBest) {
            prefs.edit().putInt(KEY_BEST_SCORE + level, score).apply()
        }
    }
    
    // Получить лучший результат для уровня
    fun getBestScore(level: Int): Int {
        return prefs.getInt(KEY_BEST_SCORE + level, 0)
    }
}



