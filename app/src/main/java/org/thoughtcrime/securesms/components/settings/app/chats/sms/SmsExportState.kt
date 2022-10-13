package org.thoughtcrime.securesms.components.settings.app.chats.sms

enum class SmsExportState {
  FETCHING,
  HAS_UNEXPORTED_MESSAGES,
  ALL_MESSAGES_EXPORTED,
  NO_SMS_MESSAGES_IN_DATABASE,
  NOT_AVAILABLE
}
