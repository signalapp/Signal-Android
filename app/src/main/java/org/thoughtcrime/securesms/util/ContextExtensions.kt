package org.thoughtcrime.securesms.util

import android.content.BroadcastReceiver
import android.content.Context

fun Context.safeUnregisterReceiver(receiver: BroadcastReceiver?) {
  if (receiver == null) {
    return
  }

  try {
    unregisterReceiver(receiver)
  } catch (e: IllegalArgumentException) {
  }
}
