package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class Broadcaster(private val context: Context) : org.whispersystems.signalservice.loki.utilities.Broadcaster {

    override fun broadcast(event: String) {
        val intent = Intent(event)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    override fun broadcast(event: String, long: Long) {
        val intent = Intent(event)
        intent.putExtra("long", long)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}