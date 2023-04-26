package org.whispersystems.signalservice.api.messages

data class SignalServiceEditMessage(
  val targetSentTimestamp: Long,
  val dataMessage: SignalServiceDataMessage
)
