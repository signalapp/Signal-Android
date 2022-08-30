package org.thoughtcrime.securesms.components.settings.app.chats.sms

data class SmsSettingsState(
  val useAsDefaultSmsApp: Boolean,
  val smsDeliveryReportsEnabled: Boolean,
  val wifiCallingCompatibilityEnabled: Boolean,
  val smsExportState: SmsExportState = SmsExportState.FETCHING
) {
  enum class SmsExportState {
    FETCHING,
    HAS_UNEXPORTED_MESSAGES,
    ALL_MESSAGES_EXPORTED,
    NO_SMS_MESSAGES_IN_DATABASE,
    NOT_AVAILABLE
  }
}
