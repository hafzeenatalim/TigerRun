package com.games.tigerrun.dgnvm

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Игровые объекты
    private var tiger: Tiger? = null
    private val obstacles = mutableListOf<Obstacle>()
    private val artifacts = mutableListOf<Artifact>()
    
    // Игровая логика
    private var score = 0
    private var gameRunning = false
    private var gamePaused = false
    private var gameSpeed = 10f
    private var baseSpeed = 10f
    private var currentGameLevel = 1
    private var startLevel = 1
    private var maxReachedLevel = 1  // Максимальный достигнутый уровень в текущей игре
    
    // Фоны для уровней
    private val backgrounds = mutableListOf<Int>()
    private var currentBackground: Drawable? = null
    
    // Временные параметры
    private var lastObstacleSpawn = 0L
    private var lastArtifactSpawn = 0L
    private var obstacleSpawnInterval = 2000L
    private var artifactSpawnInterval = 3000L
    
    // Таймеры
    private var gameThread: Thread? = null
    private var lastFrameTime = System.currentTimeMillis()
    
    // Callbacks
    private var scoreUpdateListener: ((Int) -> Unit)? = null
    private var levelChangeListener: ((Int) -> Unit)? = null
    private var gameOverListener: ((Int, Int) -> Unit)? = null  // score, maxLevel
    private var speedUpdateListener: ((Float) -> Unit)? = null
    
    // Метрики и helper для dp
    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    // Визуальные размеры (приближение сцены)
    private val tigerHeightDp = 120f
    private val obstacleHeightDp = 80f
    private val artifactSizeDp = 64f
    private val bottomMarginDp = -10f  // Небольшой отступ вниз от края
    
    init {
        // Загружаем фоны
        backgrounds.apply {
            add(R.drawable.background_level_1)
            add(R.drawable.background_level_2)
            add(R.drawable.background_level_3)
            add(R.drawable.background_level_4)
            add(R.drawable.background_level_5)
        }
    }
    
    fun setScoreUpdateListener(listener: (Int) -> Unit) {
        scoreUpdateListener = listener
    }
    
    fun setLevelChangeListener(listener: (Int) -> Unit) {
        levelChangeListener = listener
    }
    
    fun setGameOverListener(listener: (Int, Int) -> Unit) {
        gameOverListener = listener
    }
    
    fun setSpeedUpdateListener(listener: (Float) -> Unit) {
        speedUpdateListener = listener
    }
    
    fun startGame(level: Int) {
        startLevel = level
        currentGameLevel = level
        maxReachedLevel = level  // Сбрасываем максимум при начале новой игры
        
        // Начинаем с накопленных очков в зависимости от уровня
        score = when(level) {
            1 -> 0
            2 -> 1500
            3 -> 3600  // 1500 + 2100
            4 -> 6600  // 3600 + 3000
            5 -> 10800 // 6600 + 4200
            else -> 0
        }
        
        gameSpeed = baseSpeed + (level - 1) * 4f
        
        obstacles.clear()
        artifacts.clear()
        
        // Создаем тигра
        tiger = Tiger(context, 100f, 0f, dp(tigerHeightDp), dp(obstacleHeightDp))
        
        // Устанавливаем фон
        updateBackground()
        
        gameRunning = true
        gamePaused = false
        
        lastObstacleSpawn = System.currentTimeMillis()
        lastArtifactSpawn = System.currentTimeMillis()
        
        scoreUpdateListener?.invoke(score)
        levelChangeListener?.invoke(currentGameLevel)
        speedUpdateListener?.invoke(gameSpeed)
        
        startGameLoop()
    }
    
    fun pauseGame() {
        gamePaused = true
    }
    
    fun resumeGame() {
        if (gameRunning) {
            gamePaused = false
            lastFrameTime = System.currentTimeMillis()
        }
    }
    
    fun isGameStarted(): Boolean = gameRunning
    
    private fun updateBackground() {
        val bgIndex = (currentGameLevel - 1) % backgrounds.size
        currentBackground = context.getDrawable(backgrounds[bgIndex])
    }
    
    private fun startGameLoop() {
        gameThread?.interrupt()
        gameThread = Thread {
            while (gameRunning) {
                if (!gamePaused) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = (currentTime - lastFrameTime) / 1000f
                    lastFrameTime = currentTime
                    
                    update(deltaTime)
                    postInvalidate()
                    
                    try {
                        Thread.sleep(16) // ~60 FPS
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        gameThread?.start()
    }
    
    private fun update(deltaTime: Float) {
        tiger?.update(deltaTime)
        
        // Обновляем препятствия
        obstacles.forEach { it.update(gameSpeed) }
        obstacles.removeAll { it.x + it.width < 0 }
        
        // Обновляем артефакты
        artifacts.forEach { it.update(gameSpeed) }
        artifacts.removeAll { it.x + it.width < 0 }
        
        // Спавним новые препятствия
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastObstacleSpawn > obstacleSpawnInterval) {
            spawnObstacle()
            lastObstacleSpawn = currentTime
        }
        
        // Спавним новые артефакты
        if (currentTime - lastArtifactSpawn > artifactSpawnInterval) {
            spawnArtifact()
            lastArtifactSpawn = currentTime
        }
        
        // Проверяем коллизии
        checkCollisions()
        
        // Увеличиваем счет
        score++
        if (score % 30 == 0) {
            scoreUpdateListener?.invoke(score)
        }
        
        // Увеличиваем скорость постепенно (каждые 300 очков)
        if (score % 300 == 0 && score > 0) {
            gameSpeed += 1f
            speedUpdateListener?.invoke(gameSpeed)
        }
        
        // Проверяем смену уровня по накопленным очкам
        val calculatedLevel = when {
            score >= 10800 -> 5  // 1500 + 2100 + 3000 + 4200
            score >= 6600 -> 4   // 1500 + 2100 + 3000
            score >= 3600 -> 3   // 1500 + 2100
            score >= 1500 -> 2   // первый порог
            else -> 1
        }
        
        // Учитываем стартовый уровень
        val actualLevel = kotlin.math.max(calculatedLevel, startLevel)
        
        if (actualLevel > currentGameLevel && actualLevel <= 5) {
            currentGameLevel = actualLevel
            maxReachedLevel = kotlin.math.max(maxReachedLevel, currentGameLevel)
            // Увеличиваем базовую скорость при смене уровня
            gameSpeed = baseSpeed + (currentGameLevel - 1) * 4f
            updateBackground()
            levelChangeListener?.invoke(currentGameLevel)
            speedUpdateListener?.invoke(gameSpeed)
            
            // Разблокируем уровень сразу во время игры
            post {
                (context as? GameActivity)?.unlockLevelDuringGame(actualLevel)
            }
        }
    }
    
    private fun spawnObstacle() {
        val groundY = height - dp(bottomMarginDp)
        val d = context.getDrawable(R.drawable.obstacle)!!
        val targetH = dp(obstacleHeightDp)
        val ratio = if (d.intrinsicHeight > 0) d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat() else 1f
        val targetW = targetH * ratio
        val obstacle = Obstacle(
            context,
            width.toFloat(),
            groundY - targetH,
            targetW,
            targetH
        )
        obstacles.add(obstacle)
    }
    
    private fun spawnArtifact() {
        val groundY = height - dp(bottomMarginDp)
        val size = dp(artifactSizeDp)
        // Артефакты на разной высоте, но в пределах экрана
        val heights = listOf(
            groundY - size * 1.2f,
            groundY - size * 2.2f,
            groundY - size * 3.2f
        )
        val artifact = Artifact(
            context,
            width.toFloat(),
            heights[Random.nextInt(heights.size)],
            size,
            size
        )
        artifacts.add(artifact)
    }
    
    private fun checkCollisions() {
        tiger?.let { t ->
            val tigerRect = t.getCollisionRect()
            // Проверка столкновений с препятствиями
            for (obstacle in obstacles) {
                if (RectF.intersects(tigerRect, obstacle.getCollisionRect())) {
                    gameOver()
                    return
                }
            }

            // Проверка сбора артефактов
            val collectedArtifacts = artifacts.filter {
                RectF.intersects(tigerRect, it.getCollisionRect())
            }
            for (artifact in collectedArtifacts) {
                score += 50  // Бонус за артефакт
                scoreUpdateListener?.invoke(score)
            }
            artifacts.removeAll(collectedArtifacts)
        }
    }
    
    private fun gameOver() {
        gameRunning = false
        gamePaused = false
        
        // Финальная проверка максимального достигнутого уровня
        val finalLevel = when {
            score >= 10800 -> 5
            score >= 6600 -> 4
            score >= 3600 -> 3
            score >= 1500 -> 2
            else -> 1
        }
        maxReachedLevel = kotlin.math.max(maxReachedLevel, kotlin.math.max(finalLevel, startLevel))
        
        gameOverListener?.invoke(score, maxReachedLevel)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (width == 0 || height == 0) return
        
        // Рисуем фон
        currentBackground?.let {
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
        }
        
        // Нижняя опора по экрану (без отдельной полосы земли)
        val groundY = height - dp(bottomMarginDp)
        
        // Обновляем позицию земли для тигра
        tiger?.groundY = groundY
        
        // Рисуем игровые объекты (копируем списки для безопасной итерации)
        obstacles.toList().forEach { it.draw(canvas) }
        artifacts.toList().forEach { it.draw(canvas) }
        tiger?.draw(canvas)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && gameRunning && !gamePaused) {
            tiger?.jump()
            return true
        }
        return super.onTouchEvent(event)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Пересчитываем позиции при изменении размера экрана
        if (gameRunning && tiger != null) {
            val groundY = h - dp(bottomMarginDp)
            tiger?.groundY = groundY
            
            // Пересоздаем тигра с новыми параметрами
            tiger = Tiger(context, 100f, 0f, dp(tigerHeightDp), dp(obstacleHeightDp))
            tiger?.groundY = groundY
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameRunning = false
        gameThread?.interrupt()
    }
}

// Класс Tiger
class Tiger(
    private val context: Context,
    var x: Float,
    initialY: Float,
    private val targetHeightPx: Float,
    private val obstacleHeightPx: Float
) {
    var y: Float = initialY
    var groundY: Float = 0f
        set(value) {
            field = value
            if (!isJumping) {
                y = value - height
            }
        }
    
    val height = targetHeightPx
    private val drawable1: Drawable = context.getDrawable(R.drawable.tiger_1)!!
    private val drawable2: Drawable = context.getDrawable(R.drawable.tiger_2)!!
    private val drawableJump: Drawable = context.getDrawable(R.drawable.tiger_jump)!!
    private val tigerAspect: Float = run {
        val h = if (drawable1.intrinsicHeight > 0) drawable1.intrinsicHeight else 1
        val w = if (drawable1.intrinsicWidth > 0) drawable1.intrinsicWidth else 1
        w.toFloat() / h.toFloat()
    }
    val width = height * tigerAspect
    
    private var velocityY = 0f
    private var isJumping = false
    private var jumpsLeft = 2  // Количество доступных прыжков (двойной прыжок)
    private val gravity = 1500f
    // Прыжок должен быть в 1.2 раза выше препятствия
    // Формула: jumpForce = sqrt(2 * gravity * maxHeight)
    private val jumpForce = -kotlin.math.sqrt(2f * gravity * obstacleHeightPx * 1.2f)
    
    // Анимация
    private var animationFrame = 0
    private var animationTimer = 0f
    private val animationSpeed = 0.5f  // Меняется каждые 0.5 секунды
    private val density = context.resources.displayMetrics.density
    
    
    
    fun update(deltaTime: Float) {
        // Обновляем анимацию
        if (!isJumping) {
            animationTimer += deltaTime
            if (animationTimer >= animationSpeed) {
                animationTimer = 0f
                animationFrame = (animationFrame + 1) % 2
            }
        }
        
        // Физика прыжка
        if (isJumping) {
            velocityY += gravity * deltaTime
            y += velocityY * deltaTime
            
            // Проверяем приземление
            if (y >= groundY - height) {
                y = groundY - height
                isJumping = false
                velocityY = 0f
                jumpsLeft = 2  // Восстанавливаем прыжки при приземлении
            }
        }
    }
    
    fun jump() {
        if (jumpsLeft > 0) {
            if (!isJumping) {
                isJumping = true
            }
            velocityY = jumpForce
            jumpsLeft--
        }
    }
    
    fun draw(canvas: Canvas) {
        // Используем специальное изображение во время прыжка
        val drawable = if (isJumping) {
            drawableJump
        } else {
            if (animationFrame == 0) drawable1 else drawable2
        }
        
        // Опускаем спрайт ниже, чтобы тигр касался низа экрана
        val drawBottomExtra = 30f * density
        drawable.setBounds(
            x.toInt(),
            y.toInt(),
            (x + width).toInt(),
            (y + height + drawBottomExtra).toInt()
        )
        drawable.draw(canvas)
    }
    
    fun getCollisionRect(): RectF {
        // Отступы ~20% чтобы прозрачные части не задевали препятствия
        val insetX = width * 0.20f
        val insetY = height * 0.20f
        return RectF(x + insetX, y + insetY, x + width - insetX, y + height - insetY)
    }
    
    fun collidesWith(other: GameObject): Boolean {
        return x < other.x + other.width &&
                x + width > other.x &&
                y < other.y + other.height &&
                y + height > other.y
    }
}

// Базовый класс для игровых объектов
abstract class GameObject(
    val context: Context,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float
) {
    open fun getCollisionRect(): RectF = RectF(x, y, x + width, y + height)
}

// Класс Obstacle
class Obstacle(
    context: Context,
    x: Float,
    y: Float,
    width: Float,
    height: Float
) : GameObject(context, x, y, width, height) {
    
    private val drawable: Drawable = context.getDrawable(R.drawable.obstacle)!!
    
    fun update(speed: Float) {
        x -= speed
    }
    
    fun draw(canvas: Canvas) {
        drawable.setBounds(
            x.toInt(),
            y.toInt(),
            (x + width).toInt(),
            (y + height).toInt()
        )
        drawable.draw(canvas)
    }

    // Отступы ~20% чтобы прозрачные края не засчитывались
    override fun getCollisionRect(): RectF {
        val insetX = width * 0.20f
        val insetY = height * 0.20f
        return RectF(x + insetX, y + insetY, x + width - insetX, y + height - insetY)
    }
}

// Класс Artifact
class Artifact(
    context: Context,
    x: Float,
    y: Float,
    width: Float,
    height: Float
) : GameObject(context, x, y, width, height) {
    
    private val drawable: Drawable = context.getDrawable(R.drawable.artifact)!!
    private val insetXPct = 0.1f
    private val insetYPct = 0.1f
    
    fun update(speed: Float) {
        x -= speed
    }
    
    fun draw(canvas: Canvas) {
        drawable.setBounds(
            x.toInt(),
            y.toInt(),
            (x + width).toInt(),
            (y + height).toInt()
        )
        drawable.draw(canvas)
    }

    override fun getCollisionRect(): RectF {
        val dx = width * insetXPct
        val dy = height * insetYPct
        return RectF(x + dx, y + dy, x + width - dx, y + height - dy)
    }
}



