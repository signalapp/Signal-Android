package org.thoughtcrime.securesms.loki.redesign.utilities

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager

class Broadcaster(private val context: Context) : org.whispersystems.signalservice.loki.utilities.Broadcaster {

    override fun broadcast(event: String, long: Long) {
        val intent = Intent(event)
        intent.putExtra("long", long)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}