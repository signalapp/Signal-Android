package org.signal.smsexporter

import android.content.Context
import org.signal.smsexporter.internal.BecomeDefaultSmsUseCase
import org.signal.smsexporter.internal.IsDefaultSms
import org.signal.smsexporter.internal.ReleaseDefaultSmsUseCase

/**
 * Basic API for checking / becoming / releasing default SMS
 */
object DefaultSmsHelper {
  /**
   * Checks whether this app is currently the default SMS app
   */
  fun isDefaultSms(context: Context) = IsDefaultSms.checkIsDefaultSms(context)

  /**
   * Attempts to get an Intent which can be launched to become the default SMS app
   */
  fun becomeDefaultSms(context: Context) = BecomeDefaultSmsUseCase.execute(context)

  /**
   * Attempts to get an Intent which can be launched to relinquish the role of default SMS app
   */
  fun releaseDefaultSms(context: Context) = ReleaseDefaultSmsUseCase.execute(context)
}
