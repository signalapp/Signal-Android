package org.thoughtcrime.securesms.loki.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import network.loki.messenger.R
import org.session.libsession.snode.OnionRequestAPI
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.loki.utilities.toPx

class PathStatusView : View {
    private val broadcastReceivers = mutableListOf<BroadcastReceiver>()
    @ColorInt var mainColor: Int = 0
        set(newValue) { field = newValue; paint.color = newValue }
    @ColorInt var sessionShadowColor: Int = 0
        set(newValue) { field = newValue; paint.setShadowLayer(toPx(8, resources).toFloat(), 0.0f, 0.0f, newValue) }

    private val paint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.FILL
        result.isAntiAlias = true
        result
    }

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize()
    }

    private fun initialize() {
        update()
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerObservers()
    }

    private fun registerObservers() {
        val buildingPathsReceiver: BroadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                handleBuildingPathsEvent()
            }
        }
        broadcastReceivers.add(buildingPathsReceiver)
        LocalBroadcastManager.getInstance(context).registerReceiver(buildingPathsReceiver, IntentFilter("buildingPaths"))
        val pathsBuiltReceiver: BroadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                handlePathsBuiltEvent()
            }
        }
        broadcastReceivers.add(pathsBuiltReceiver)
        LocalBroadcastManager.getInstance(context).registerReceiver(pathsBuiltReceiver, IntentFilter("pathsBuilt"))
    }

    override fun onDetachedFromWindow() {
        for (receiver in broadcastReceivers) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
        super.onDetachedFromWindow()
    }

    private fun handleBuildingPathsEvent() { update() }
    private fun handlePathsBuiltEvent() { update() }

    private fun update() {
        if (OnionRequestAPI.paths.isNotEmpty()) {
            setBackgroundResource(R.drawable.accent_dot)
            mainColor = resources.getColorWithID(R.color.accent, context.theme)
            sessionShadowColor = resources.getColorWithID(R.color.accent, context.theme)
        } else {
            setBackgroundResource(R.drawable.paths_building_dot)
            mainColor = resources.getColorWithID(R.color.paths_building, context.theme)
            sessionShadowColor = resources.getColorWithID(R.color.paths_building, context.theme)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2, h / 2, w / 2, paint)
        super.onDraw(c)
    }
}