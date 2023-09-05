package org.thoughtcrime.securesms.megaphone

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.util.Util

class SmsExportReminderSchedule(private val context: Context) : MegaphoneSchedule {

  companion object {
    @JvmStatic
    var showPhase3Megaphone = true
  }

  @WorkerThread
  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    return if (Util.isDefaultSmsProvider(context)) {
      showPhase3Megaphone
    } else {
      false
    }
  }
}
