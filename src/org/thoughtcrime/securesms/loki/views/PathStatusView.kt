package org.thoughtcrime.securesms.loki.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.AttributeSet
import android.view.View
import network.loki.messenger.R
import org.whispersystems.signalservice.loki.api.onionrequests.OnionRequestAPI

class PathStatusView : View {
    private val broadcastReceivers = mutableListOf<BroadcastReceiver>()

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
        if (OnionRequestAPI.paths.count() >= OnionRequestAPI.pathCount) {
            setBackgroundResource(R.drawable.accent_dot)
        } else {
            setBackgroundResource(R.drawable.paths_building_dot)
        }
    }
}