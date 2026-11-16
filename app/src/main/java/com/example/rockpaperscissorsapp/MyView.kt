package com.example.rockpaperscissorsapp

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

class MyView(context: Context) : FrameLayout(context) {

    companion object {
        const val LION = 0
        const val COBRA = 1
        const val RABBIT = 2
        val NAMES = arrayOf("Lion", "Cobra", "Rabbit")
        val LION_FRAMES = intArrayOf(
            R.drawable.lion0, R.drawable.lion1, R.drawable.lion2, R.drawable.lion3
        )
        val ANIMAL_IMAGES = intArrayOf(R.drawable.lion0, R.drawable.cobra, R.drawable.rabbit)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var initialized = false
    private var enabled = false          // false during intro and while a round is playing

    private var size = 0
    private lateinit var jungle: RectF
    private var playerSlotX = 0f
    private var phoneSlotX = 0f
    private var slotY = 0f

    private lateinit var title: ImageView
    private lateinit var phoneView: ImageView
    private val animals = arrayOfNulls<ImageView>(3)
    private val origX = FloatArray(3)
    private val origY = FloatArray(3)
    private lateinit var playerLabel: TextView
    private lateinit var phoneLabel: TextView
    private lateinit var resultLabel: TextView
    private var playerScore = 0
    private var phoneScore = 0

    // drag state
    private var dragging = -1
    private var dx = 0f
    private var dy = 0f
    private var lionFrame = 0
    private val lionAnim = object : Runnable {
        override fun run() {
            if (dragging == LION) {
                lionFrame = (lionFrame + 1) % 4
                animals[LION]!!.setImageResource(LION_FRAMES[lionFrame])
                handler.postDelayed(this, 100)
            }
        }
    }

    init {
        setBackgroundColor(Color.rgb(250, 250, 250))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (initialized || w == 0 || h == 0) return
        initialized = true
        // add the views after the layout pass is finished so they get measured
        post { setupViews(w, h) }
    }

    private fun setupViews(w: Int, h: Int) {
        size = (w * 0.2f).toInt()

        // jungle frame on the left (also the drop zone)
        jungle = RectF(w * 0.15f, h * 0.19f, w * 0.79f, h * 0.84f)
        val jungleView = ImageView(context)
        jungleView.setImageResource(R.drawable.jungleframe)
        jungleView.scaleType = ImageView.ScaleType.FIT_XY
        addView(jungleView, LayoutParams(jungle.width().toInt(), jungle.height().toInt()))
        jungleView.x = jungle.left
        jungleView.y = jungle.top

        // fixed spots inside the jungle
        slotY = jungle.centerY() - size / 2f
        phoneSlotX = jungle.left + w * 0.04f
        playerSlotX = jungle.right - size - w * 0.04f

        // animals in a column on the right: top, middle, bottom of the frame
        val colX = w * 0.795f
        origY[0] = jungle.top
        origY[1] = jungle.centerY() - size / 2f
        origY[2] = jungle.bottom - size
        for (i in 0..2) {
            val iv = ImageView(context)
            iv.setImageResource(ANIMAL_IMAGES[i])
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iv, LayoutParams(size, size))
            origX[i] = colX
            iv.x = origX[i]
            iv.y = origY[i]
            animals[i] = iv
        }

        // phone's animal (hidden until needed)
        phoneView = ImageView(context)
        phoneView.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(phoneView, LayoutParams(size, size))
        phoneView.alpha = 0f
        phoneView.x = phoneSlotX
        phoneView.y = slotY

        // score labels, top left (hidden until the title fades out)
        phoneLabel = makeLabel("Phone: 0", 14f, Gravity.START, Color.DKGRAY)
        playerLabel = makeLabel("Player: 0", 14f, Gravity.START, Color.DKGRAY)
        phoneLabel.alpha = 0f
        playerLabel.alpha = 0f
        addView(phoneLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(playerLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        phoneLabel.x = jungle.left
        phoneLabel.y = h * 0.05f
        playerLabel.x = jungle.left
        playerLabel.y = h * 0.05f + 70f

        // result label at the bottom of the screen, under the jungle frame
        resultLabel = makeLabel("", 16f, Gravity.CENTER, Color.DKGRAY)
        resultLabel.alpha = 0f
        addView(resultLabel, LayoutParams(w, LayoutParams.WRAP_CONTENT))
        resultLabel.x = 0f
        resultLabel.y = jungle.bottom + h * 0.04f

        // title image: zoom from tiny to the top portion, fade out, then show scores
        title = ImageView(context)
        title.setImageResource(R.drawable.title)
        title.scaleType = ImageView.ScaleType.FIT_CENTER
        val titleH = (h * 0.16f).toInt()
        addView(title, LayoutParams(w, titleH))
        title.pivotX = w / 2f
        title.pivotY = titleH / 2f
        title.scaleX = 0.02f
        title.scaleY = 0.02f
        title.animate().scaleX(1f).scaleY(1f).setDuration(2500).withEndAction {
            title.animate().alpha(0f).setDuration(1200).withEndAction {
                playerLabel.animate().alpha(1f).setDuration(1000)
                phoneLabel.animate().alpha(1f).setDuration(1000).withEndAction {
                    enabled = true
                }
            }
        }
    }

    private fun makeLabel(text: String, sp: Float, gravity: Int, color: Int): TextView {
        val t = TextView(context)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        t.gravity = gravity
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(12, 12, 12, 12)
        return t
    }

    // ---------- dragging ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!enabled) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                for (i in 0..2) {
                    val v = animals[i]!!
                    if (e.x >= v.x && e.x <= v.x + size && e.y >= v.y && e.y <= v.y + size) {
                        dragging = i
                        dx = e.x - v.x
                        dy = e.y - v.y
                        v.bringToFront()
                        if (i == LION) {
                            lionFrame = 0
                            handler.post(lionAnim)
                        }
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging >= 0) {
                    animals[dragging]!!.x = e.x - dx
                    animals[dragging]!!.y = e.y - dy
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging >= 0) {
                    val chosen = dragging
                    dragging = -1
                    handler.removeCallbacks(lionAnim)
                    animals[LION]!!.setImageResource(LION_FRAMES[0])
                    val v = animals[chosen]!!
                    val inside = v.x >= jungle.left && v.y >= jungle.top &&
                            v.x + size <= jungle.right && v.y + size <= jungle.bottom
                    enabled = false
                    if (inside) {
                        v.x = playerSlotX      // fixed location, not wherever it was dropped
                        v.y = slotY
                        faceOpponent(v, chosen, false)
                        playRound(chosen)
                    } else {
                        v.animate().x(origX[chosen]).y(origY[chosen]).setDuration(600)
                            .withEndAction { enabled = true }
                    }
                }
            }
        }
        return true
    }

    // lion and cobra images face left, rabbit faces right
    private fun faceOpponent(v: ImageView, type: Int, onLeft: Boolean) {
        val naturallyFacesLeft = type != RABBIT
        val needFlip = if (onLeft) naturallyFacesLeft else !naturallyFacesLeft
        v.scaleX = if (needFlip) -1f else 1f
    }

    // ---------- game round ----------

    private fun beats(a: Int, b: Int) =
        (a == LION && b == RABBIT) || (a == COBRA && b == LION) || (a == RABBIT && b == COBRA)

    private fun playRound(playerType: Int) {
        val phoneType = Random.nextInt(3)
        val playerView = animals[playerType]!!

        phoneView.setImageResource(ANIMAL_IMAGES[phoneType])
        phoneView.x = phoneSlotX
        phoneView.y = slotY
        faceOpponent(phoneView, phoneType, true)
        phoneView.alpha = 0f
        phoneView.bringToFront()
        playerView.bringToFront()

        phoneView.animate().alpha(1f).setDuration(1200).withEndAction {
            when {
                phoneType == playerType -> tie(playerView)
                beats(playerType, phoneType) -> playerWins(playerType, phoneType, playerView)
                else -> phoneWins(playerType, phoneType, playerView)
            }
        }
    }

    private fun showResult(msg: String, after: () -> Unit) {
        resultLabel.text = msg
        resultLabel.bringToFront()
        resultLabel.animate().alpha(1f).setDuration(1000).withEndAction { after() }
    }

    private fun tie(playerView: ImageView) {
        showResult("Tie!") {
            // everything fades out together
            phoneView.animate().alpha(0f).setDuration(1000)
            playerView.animate().alpha(0f).setDuration(1000)
            resultLabel.animate().alpha(0f).setDuration(1000).withEndAction {
                finishRound(playerView, false)
            }
        }
    }

    private fun playerWins(p: Int, c: Int, playerView: ImageView) {
        val msg = "${NAMES[p]} defeats ${NAMES[c]} - You Win!"
        // winner moves onto the loser and covers it
        playerView.animate().x(phoneSlotX).setDuration(1500).withEndAction {
            phoneView.animate().alpha(0f).setDuration(1000).withEndAction {
                showResult(msg) {
                    playerScore++
                    playerLabel.text = "Player: $playerScore"
                    resultLabel.animate().alpha(0f).setDuration(1000).withEndAction {
                        finishRound(playerView, true)
                    }
                }
            }
        }
    }

    private fun phoneWins(p: Int, c: Int, playerView: ImageView) {
        val msg = "${NAMES[c]} defeats ${NAMES[p]} - You Lose!"
        phoneView.animate().x(playerSlotX).setDuration(1500).withEndAction {
            playerView.animate().alpha(0f).setDuration(1000).withEndAction {
                showResult(msg) {
                    phoneScore++
                    phoneLabel.text = "Phone: $phoneScore"
                    resultLabel.animate().alpha(0f).setDuration(1000).withEndAction {
                        finishRound(playerView, false)
                    }
                }
            }
        }
    }

    // put everything back so the player can play again
    private fun finishRound(playerView: ImageView, animateBack: Boolean) {
        phoneView.alpha = 0f
        phoneView.x = phoneSlotX
        playerView.scaleX = 1f
        playerView.alpha = 1f
        val idx = animals.indexOf(playerView)
        if (animateBack) {
            // winner is on the left side, so glide it back to its start position
            playerView.animate().x(origX[idx]).y(origY[idx]).setDuration(700)
                .withEndAction { enabled = true }
        } else {
            playerView.x = origX[idx]
            playerView.y = origY[idx]
            enabled = true
        }
    }
}