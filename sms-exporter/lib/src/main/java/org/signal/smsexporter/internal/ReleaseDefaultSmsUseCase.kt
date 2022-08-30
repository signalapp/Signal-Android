package org.signal.smsexporter.internal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import org.signal.core.util.Result
import org.signal.smsexporter.ReleaseSmsAppFailure

/**
 * Request to no longer be the default SMS app. This has a pretty bad UX, we need
 * to get the user to manually do it in settings. On API 24+ we can launch the default
 * app settings screen, whereas on 19 to 23, we can't. In this situation, we should
 * display some UX (perhaps based off API level) explaining to the user exactly what to
 * do.
 *
 * Returns the Intent to fire off, or a Failure.
 */
internal object ReleaseDefaultSmsUseCase {
  fun execute(context: Context): Result<Intent, ReleaseSmsAppFailure> {
    return if (!IsDefaultSms.checkIsDefaultSms(context)) {
      Result.failure(ReleaseSmsAppFailure.APP_IS_INELIGIBLE_TO_RELEASE_SMS_SELECTION)
    } else if (Build.VERSION.SDK_INT >= 24) {
      Result.success(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
      )
    } else {
      Result.failure(ReleaseSmsAppFailure.NO_METHOD_TO_RELEASE_SMS_AVIALABLE)
    }
  }
}
