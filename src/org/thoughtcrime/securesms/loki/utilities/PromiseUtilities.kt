package org.thoughtcrime.securesms.loki.utilities

import android.util.Log
import nl.komponents.kovenant.Promise

fun <V, E> Promise<V, E>.successBackground(callback: (value: V) -> Unit): Promise<V, E> {
    Thread {
        try {
            callback(get())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to execute task in background: ${e.message}.")
        }
    }.start()
    return this
}