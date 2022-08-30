package org.signal.smsexporter

enum class ReleaseSmsAppFailure {
  /**
   * Occurs when we are not the default sms app
   */
  APP_IS_INELIGIBLE_TO_RELEASE_SMS_SELECTION,

  /**
   * No good way to release sms. Have to instruct user manually.
   */
  NO_METHOD_TO_RELEASE_SMS_AVIALABLE
}
