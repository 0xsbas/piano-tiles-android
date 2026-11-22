package com.example.pianotiles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class PianoTilesView(context: Context) : SurfaceView(context), Runnable {

    private enum class ScreenState { MENU, PLAYING, GAME_OVER }

    private data class Tile(
        val lane: Int,
        var top: Float,
        val height: Float,
        val midiNote: Int,
        var hit: Boolean = false,
        var failed: Boolean = false
    )

    private var thread: Thread? = null
    @Volatile private var running = false

    private var state = ScreenState.MENU
    private var selectedSongIndex = 0
    private val tiles = ArrayList<Tile>()
    private var score = 0
    private var bestScore = 0
    private var noteCursor = 0
    private var currentSpeed = 750f
    private var failReason = ""

    private val songButtons = ArrayList<RectF>()
    private val retryButton = RectF()
    private val menuButton = RectF()

    private val bgPaint = Paint().apply { color = Color.parseColor("#FAFAFA") }
    private val laneLinePaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 3f
    }
    private val tilePaint = Paint().apply {
        color = Color.parseColor("#141414")
        isAntiAlias = true
    }
    private val hitTilePaint = Paint().apply {
        color = Color.parseColor("#BDBDBD")
        isAntiAlias = true
    }
    private val failTilePaint = Paint().apply {
        color = Color.parseColor("#E53935")
        isAntiAlias = true
    }
    private val cardPaint = Paint().apply {
        color = Color.parseColor("#141414")
        isAntiAlias = true
    }
    private val outlineCardPaint = Paint().apply {
        color = Color.parseColor("#141414")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.parseColor("#141414")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val subPaint = Paint().apply {
        color = Color.parseColor("#616161")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val cardTitlePaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val cardSubPaint = Paint().apply {
        color = Color.parseColor("#BDBDBD")
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }
    private val scorePaint = Paint().apply {
        color = Color.parseColor("#E53935")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    init {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                resume()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pause()
            }
        })
    }

    fun resume() {
        if (running) return
        running = true
        thread = Thread(this).apply { start() }
    }

    fun pause() {
        running = false
        try {
            thread?.join(300)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    override fun run() {
        var lastTime = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            lastTime = now

            update(dt)
            render()
        }
    }

    private fun startGame(songIdx: Int) {
        selectedSongIndex = songIdx
        val song = SongCatalog.songs[songIdx]
        score = 0
        noteCursor = 0
        currentSpeed = song.baseSpeed * (height / 1920f).coerceAtLeast(0.75f)
        failReason = ""

        synchronized(tiles) {
            tiles.clear()
            val tileHeight = height / 4f
            // Spawn 5 initial tiles above the bottom edge
            for (i in 0 until 5) {
                val top = height * 0.55f - (i + 1) * tileHeight
                tiles.add(createNextTile(top, tileHeight))
            }
        }
        state = ScreenState.PLAYING
    }

    private fun createNextTile(top: Float, tileHeight: Float): Tile {
        val song = SongCatalog.songs[selectedSongIndex]
        val midi = song.notes[noteCursor % song.notes.size]
        noteCursor++
        val prevLane = tiles.lastOrNull()?.lane ?: -1
        var lane = Random.nextInt(4)
        if (lane == prevLane) lane = (lane + Random.nextInt(1, 4)) % 4
        return Tile(lane = lane, top = top, height = tileHeight, midiNote = midi)
    }

    private fun update(dt: Float) {
        if (state != ScreenState.PLAYING || width == 0 || height == 0) return

        val tileHeight = height / 4f
        synchronized(tiles) {
            for (tile in tiles) {
                tile.top += currentSpeed * dt
                if (!tile.hit && tile.top > height) {
                    tile.failed = true
                    failReason = "Missed a tile"
                    triggerGameOver()
                    return
                }
            }

            // Remove tiles far below screen
            tiles.removeAll { it.top > height + tileHeight }

            // Spawn new tiles at the top seamlessly
            while (tiles.isNotEmpty() && tiles.last().top > -tileHeight) {
                val nextTop = tiles.last().top - tileHeight
                tiles.add(createNextTile(nextTop, tileHeight))
            }
        }

        // Gradual tempo acceleration
        currentSpeed += 12f * dt
    }

    private fun triggerGameOver() {
        if (score > bestScore) bestScore = score
        state = ScreenState.GAME_OVER
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) {
            return true
        }

        val idx = event.actionIndex
        val x = event.getX(idx)
        val y = event.getY(idx)

        when (state) {
            ScreenState.MENU -> {
                for (i in songButtons.indices) {
                    if (songButtons[i].contains(x, y)) {
                        PianoSynth.playMidiNote(SongCatalog.songs[i].notes[0], 250)
                        startGame(i)
                        return true
                    }
                }
            }
            ScreenState.PLAYING -> {
                handleGameTap(x, y)
            }
            ScreenState.GAME_OVER -> {
                if (retryButton.contains(x, y)) {
                    startGame(selectedSongIndex)
                } else if (menuButton.contains(x, y)) {
                    state = ScreenState.MENU
                }
            }
        }
        return true
    }

    private fun handleGameTap(x: Float, y: Float) {
        if (width == 0) return
        val laneWidth = width / 4f
        val tappedLane = (x / laneWidth).toInt().coerceIn(0, 3)

        synchronized(tiles) {
            // Find the lowest unhit tile on screen
            val target = tiles.firstOrNull { !it.hit }
            if (target != null && target.lane == tappedLane &&
                y >= target.top - target.height * 0.25f &&
                y <= target.top + target.height * 1.25f
            ) {
                target.hit = true
                score++
                PianoSynth.playMidiNote(target.midiNote)
                return
            }

            // Check if player tapped a higher tile out of order or tapped an empty white lane
            val anyTileInLane = tiles.firstOrNull {
                !it.hit && it.lane == tappedLane && y >= it.top && y <= it.top + it.height
            }
            if (anyTileInLane != null) {
                anyTileInLane.failed = true
                failReason = "Tapped out of order"
            } else {
                // Mark a synthetic red error block where the user mis-tapped
                val tileHeight = height / 4f
                tiles.add(
                    Tile(
                        lane = tappedLane,
                        top = (y - tileHeight / 2f).coerceIn(0f, height - tileHeight),
                        height = tileHeight,
                        midiNote = 0,
                        failed = true
                    )
                )
                failReason = "Tapped empty lane"
            }
            PianoSynth.playMidiNote(42, 300) // Low dissonance note
            triggerGameOver()
        }
    }

    private fun render() {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            when (state) {
                ScreenState.MENU -> drawMenu(canvas)
                ScreenState.PLAYING -> drawBoard(canvas)
                ScreenState.GAME_OVER -> {
                    drawBoard(canvas)
                    drawGameOverOverlay(canvas)
                }
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMenu(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        titlePaint.textSize = w * 0.11f
        canvas.drawText("PIANO TILES", w / 2f, h * 0.16f, titlePaint)

        subPaint.textSize = w * 0.042f
        canvas.drawText("Public Domain Classical Edition", w / 2f, h * 0.21f, subPaint)

        songButtons.clear()
        val marginX = w * 0.1f
        val cardHeight = h * 0.115f
        val gap = h * 0.025f
        var top = h * 0.28f

        cardTitlePaint.textSize = w * 0.052f
        cardSubPaint.textSize = w * 0.036f

        for (song in SongCatalog.songs) {
            val rect = RectF(marginX, top, w - marginX, top + cardHeight)
            songButtons.add(rect)
            canvas.drawRoundRect(rect, 24f, 24f, cardPaint)
            canvas.drawText(song.title, rect.left + 44f, rect.top + cardHeight * 0.45f, cardTitlePaint)
            canvas.drawText(song.composer, rect.left + 44f, rect.top + cardHeight * 0.78f, cardSubPaint)
            top += cardHeight + gap
        }
    }

    private fun drawBoard(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val laneWidth = w / 4f

        // 4 vertical lane dividers
        for (i in 1..3) {
            val x = laneWidth * i
            canvas.drawLine(x, 0f, x, h, laneLinePaint)
        }

        synchronized(tiles) {
            for (tile in tiles) {
                val left = tile.lane * laneWidth + 3f
                val right = (tile.lane + 1) * laneWidth - 3f
                val paint = when {
                    tile.failed -> failTilePaint
                    tile.hit -> hitTilePaint
                    else -> tilePaint
                }
                canvas.drawRect(left, tile.top + 2f, right, tile.top + tile.height - 2f, paint)
            }
        }

        scorePaint.textSize = w * 0.13f
        canvas.drawText(score.toString(), w / 2f, h * 0.11f, scorePaint)
    }

    private fun drawGameOverOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Translucent white overlay
        val overlayPaint = Paint().apply { color = Color.argb(225, 250, 250, 250) }
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        titlePaint.textSize = w * 0.10f
        canvas.drawText("GAME OVER", w / 2f, h * 0.28f, titlePaint)

        subPaint.textSize = w * 0.045f
        canvas.drawText(failReason, w / 2f, h * 0.34f, subPaint)

        scorePaint.textSize = w * 0.18f
        canvas.drawText(score.toString(), w / 2f, h * 0.47f, scorePaint)

        subPaint.textSize = w * 0.042f
        canvas.drawText("Best: $bestScore", w / 2f, h * 0.53f, subPaint)

        val btnWidth = w * 0.65f
        val btnHeight = h * 0.08f
        val left = (w - btnWidth) / 2f

        retryButton.set(left, h * 0.60f, left + btnWidth, h * 0.60f + btnHeight)
        canvas.drawRoundRect(retryButton, 20f, 20f, cardPaint)
        cardTitlePaint.textAlign = Paint.Align.CENTER
        cardTitlePaint.textSize = w * 0.05f
        canvas.drawText("PLAY AGAIN", w / 2f, retryButton.centerY() + w * 0.018f, cardTitlePaint)

        menuButton.set(left, h * 0.71f, left + btnWidth, h * 0.71f + btnHeight)
        canvas.drawRoundRect(menuButton, 20f, 20f, outlineCardPaint)
        titlePaint.textSize = w * 0.05f
        canvas.drawText("CHANGE SONG", w / 2f, menuButton.centerY() + w * 0.018f, titlePaint)

        // Reset alignment for menu cards
        cardTitlePaint.textAlign = Paint.Align.LEFT
    }
}
