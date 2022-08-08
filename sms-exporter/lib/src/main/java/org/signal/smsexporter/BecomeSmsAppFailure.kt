package org.signal.smsexporter

enum class BecomeSmsAppFailure {
  /**
   * Already the default sms app
   */
  ALREADY_DEFAULT_SMS,

  /**
   * The system doesn't think we are allowed to become the sms app
   */
  ROLE_IS_NOT_AVAILABLE
}
