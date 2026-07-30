package com.michis.reader.spen

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class SpenRemoteController(
    context: Context,
    private val gestureReceived: (SpenControlPreferences.Gesture) -> Unit
) {
    private val activityContext = context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val spenRemote = SpenRemote.getInstance()
    private var unitManager: SpenUnitManager? = null
    private var buttonUnit: SpenUnit? = null
    private var motionUnit: SpenUnit? = null
    private var previousButtonRelease = 0L
    private var pendingSingleClick: Runnable? = null
    private var totalX = 0f
    private var totalY = 0f
    private var pathDistance = 0f
    private var accumulatedAngle = 0f
    private var previousDirection: Float? = null
    private var finishMotion: Runnable? = null
    private var buttonIsPressed = false
    var isConnected: Boolean = false
        private set

    private val buttonListener = SpenEventListener { event ->
        val button = ButtonEvent(event)
        mainHandler.post {
            when (button.action) {
                ButtonEvent.ACTION_DOWN -> {
                    buttonIsPressed = true
                    finishMotion?.let(mainHandler::removeCallbacks); finishMotion = null; resetMotion()
                }
                ButtonEvent.ACTION_UP -> {
                    buttonIsPressed = false
                    if (pathDistance >= MINIMUM_GESTURE_DISTANCE) recognizeMotion() else processButtonRelease()
                }
            }
        }
    }
    private val motionListener = SpenEventListener { event ->
        val motion = AirMotionEvent(event)
        mainHandler.post { processMotion(motion.deltaX, motion.deltaY) }
    }

    fun connect() {
        runCatching {
            if (spenRemote.isConnected) {
                // Una actividad anterior pudo dejar la conexión viva; la renovamos para registrar sus listeners aquí.
                spenRemote.disconnect(activityContext)
            }
            spenRemote.connect(activityContext, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    isConnected = true
                    unitManager = manager
                    registerAvailableUnits(manager)
                }
                override fun onFailure(error: Int) = Unit
            })
        }
    }

    private fun registerAvailableUnits(manager: SpenUnitManager) {
        runCatching {
            if (spenRemote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON)) {
                buttonUnit = manager.getUnit(SpenUnit.TYPE_BUTTON)?.also { manager.registerSpenEventListener(buttonListener, it) }
            }
            if (spenRemote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION)) {
                motionUnit = manager.getUnit(SpenUnit.TYPE_AIR_MOTION)?.also { manager.registerSpenEventListener(motionListener, it) }
            }
        }
    }

    fun disconnect() {
        isConnected = false
        pendingSingleClick?.let(mainHandler::removeCallbacks)
        finishMotion?.let(mainHandler::removeCallbacks)
        runCatching { buttonUnit?.let { unitManager?.unregisterSpenEventListener(it) } }
        runCatching { motionUnit?.let { unitManager?.unregisterSpenEventListener(it) } }
        runCatching { if (spenRemote.isConnected) spenRemote.disconnect(activityContext) }
        buttonUnit = null; motionUnit = null; unitManager = null; resetMotion()
    }

    private fun processButtonRelease() {
        val timestamp = android.os.SystemClock.uptimeMillis()
        if (timestamp - previousButtonRelease in 1..DOUBLE_CLICK_MILLISECONDS) {
            pendingSingleClick?.let(mainHandler::removeCallbacks); pendingSingleClick = null; previousButtonRelease = 0L
            gestureReceived(SpenControlPreferences.gestures[1])
        } else {
            previousButtonRelease = timestamp
            pendingSingleClick = Runnable {
                pendingSingleClick = null; previousButtonRelease = 0L
                gestureReceived(SpenControlPreferences.gestures[0])
            }.also { mainHandler.postDelayed(it, DOUBLE_CLICK_MILLISECONDS) }
        }
    }

    private fun processMotion(deltaX: Float, deltaY: Float) {
        if (!buttonIsPressed) return
        val distance = hypot(deltaX, deltaY)
        if (distance < .01f) return
        totalX += deltaX; totalY += deltaY; pathDistance += distance
        val direction = atan2(deltaY, deltaX)
        previousDirection?.let { previous ->
            var difference = direction - previous
            while (difference > Math.PI) difference -= (Math.PI * 2).toFloat()
            while (difference < -Math.PI) difference += (Math.PI * 2).toFloat()
            accumulatedAngle += difference
        }
        previousDirection = direction
        finishMotion?.let(mainHandler::removeCallbacks)
        finishMotion = Runnable { finishMotion = null }.also { mainHandler.postDelayed(it, MOTION_END_MILLISECONDS) }
    }

    private fun recognizeMotion() {
        val gesture = when {
            pathDistance > 1.2f && abs(accumulatedAngle) > 4.2f -> if (accumulatedAngle < 0) 6 else 7
            abs(totalX) >= SWIPE_THRESHOLD && abs(totalX) > abs(totalY) * 1.25f -> if (totalX < 0) 2 else 3
            abs(totalY) >= SWIPE_THRESHOLD -> if (totalY > 0) 4 else 5
            else -> null
        }
        resetMotion()
        gesture?.let { gestureReceived(SpenControlPreferences.gestures[it]) }
    }

    private fun resetMotion() {
        totalX = 0f; totalY = 0f; pathDistance = 0f; accumulatedAngle = 0f; previousDirection = null
    }

    companion object {
        private const val DOUBLE_CLICK_MILLISECONDS = 350L
        private const val MOTION_END_MILLISECONDS = 180L
        private const val SWIPE_THRESHOLD = .28f
        private const val MINIMUM_GESTURE_DISTANCE = .12f
    }
}
