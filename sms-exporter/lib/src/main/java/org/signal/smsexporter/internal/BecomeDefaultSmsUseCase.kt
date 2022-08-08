package org.signal.smsexporter.internal

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.role.RoleManagerCompat
import org.signal.core.util.Result
import org.signal.smsexporter.BecomeSmsAppFailure

/**
 * Requests that this app becomes the default SMS app. The exact UX here is
 * API dependant.
 *
 * Returns an intent to fire for a result, or a Failure.
 */
internal object BecomeDefaultSmsUseCase {
  fun execute(context: Context): Result<Intent, BecomeSmsAppFailure> {
    return if (IsDefaultSms.checkIsDefaultSms(context)) {
      Result.failure(BecomeSmsAppFailure.ALREADY_DEFAULT_SMS)
    } else if (Build.VERSION.SDK_INT >= 29) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager.isRoleAvailable(RoleManagerCompat.ROLE_SMS)) {
        Result.success(roleManager.createRequestRoleIntent(RoleManagerCompat.ROLE_SMS))
      } else {
        Result.failure(BecomeSmsAppFailure.ROLE_IS_NOT_AVAILABLE)
      }
    } else {
      Result.success(
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
          .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
      )
    }
  }
}
