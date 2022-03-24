package org.thoughtcrime.securesms.sms

import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage

class OutgoingStoryMessage(
  val outgoingSecureMediaMessage: OutgoingSecureMediaMessage,
  val preUploadResult: MessageSender.PreUploadResult
)
