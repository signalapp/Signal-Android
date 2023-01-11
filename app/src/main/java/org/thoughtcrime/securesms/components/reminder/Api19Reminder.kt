package org.thoughtcrime.securesms.components.reminder

import android.content.Context
import android.os.Build
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.Util
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shown when a user has API 19.
 */
class Api19Reminder(context: Context) : Reminder(null, context.getString(R.string.API19Reminder_banner_message, getExpireDate())) {

  init {
    addAction(
      Action(
        context.getString(R.string.API19Reminder_learn_more),
        R.id.reminder_action_api_19_learn_more
      )
    )
  }

  override fun isDismissable(): Boolean {
    return false
  }

  override fun getImportance(): Importance {
    return Importance.TERMINAL
  }

  companion object {
    @JvmStatic
    fun isEligible(): Boolean {
      return Build.VERSION.SDK_INT < 21 && !ExpiredBuildReminder.isEligible()
    }

    fun getExpireDate(): String {
      val formatter = SimpleDateFormat("MMMM d", Locale.getDefault())
      val expireDate = Date(System.currentTimeMillis() + Util.getTimeUntilBuildExpiry())
      return formatter.format(expireDate)
    }
  }
}
