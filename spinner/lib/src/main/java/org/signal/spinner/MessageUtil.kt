package org.signal.spinner

object MessageUtil {
  private const val BASE_TYPE_MASK: Long = 0x1F
  private const val INCOMING_AUDIO_CALL_TYPE: Long = 1
  private const val OUTGOING_AUDIO_CALL_TYPE: Long = 2
  private const val MISSED_AUDIO_CALL_TYPE: Long = 3
  private const val JOINED_TYPE: Long = 4
  private const val UNSUPPORTED_MESSAGE_TYPE: Long = 5
  private const val INVALID_MESSAGE_TYPE: Long = 6
  private const val PROFILE_CHANGE_TYPE: Long = 7
  private const val MISSED_VIDEO_CALL_TYPE: Long = 8
  private const val GV1_MIGRATION_TYPE: Long = 9
  private const val INCOMING_VIDEO_CALL_TYPE: Long = 10
  private const val OUTGOING_VIDEO_CALL_TYPE: Long = 11
  private const val GROUP_CALL_TYPE: Long = 12
  private const val BAD_DECRYPT_TYPE: Long = 13
  private const val CHANGE_NUMBER_TYPE: Long = 14
  private const val BOOST_REQUEST_TYPE: Long = 15
  private const val BASE_INBOX_TYPE: Long = 20
  private const val BASE_OUTBOX_TYPE: Long = 21
  private const val outgoingSmsMessageType: Long = 22
  private const val BASE_SENT_TYPE: Long = 23
  private const val BASE_SENT_FAILED_TYPE: Long = 24
  private const val BASE_PENDING_SECURE_SMS_FALLBACK: Long = 25
  private const val BASE_PENDING_INSECURE_SMS_FALLBACK: Long = 26
  private const val BASE_DRAFT_TYPE: Long = 27
  private val OUTGOING_MESSAGE_TYPES = longArrayOf(BASE_OUTBOX_TYPE, BASE_SENT_TYPE, outgoingSmsMessageType, BASE_SENT_FAILED_TYPE, BASE_PENDING_SECURE_SMS_FALLBACK, BASE_PENDING_INSECURE_SMS_FALLBACK, OUTGOING_AUDIO_CALL_TYPE, OUTGOING_VIDEO_CALL_TYPE)
  private const val MESSAGE_RATE_LIMITED_BIT: Long = 0x80
  private const val MESSAGE_FORCE_SMS_BIT: Long = 0x40
  private const val KEY_EXCHANGE_BIT: Long = 0x8000
  private const val KEY_EXCHANGE_IDENTITY_VERIFIED_BIT: Long = 0x4000
  private const val KEY_EXCHANGE_IDENTITY_DEFAULT_BIT: Long = 0x2000
  private const val KEY_EXCHANGE_CORRUPTED_BIT: Long = 0x1000
  private const val KEY_EXCHANGE_INVALID_VERSION_BIT: Long = 0x800
  private const val KEY_EXCHANGE_BUNDLE_BIT: Long = 0x400
  private const val KEY_EXCHANGE_IDENTITY_UPDATE_BIT: Long = 0x200
  private const val KEY_EXCHANGE_CONTENT_FORMAT: Long = 0x100
  private const val SECURE_MESSAGE_BIT: Long = 0x800000
  private const val END_SESSION_BIT: Long = 0x400000
  private const val PUSH_MESSAGE_BIT: Long = 0x200000
  private const val GROUP_UPDATE_BIT: Long = 0x10000
  private const val GROUP_LEAVE_BIT: Long = 0x20000
  private const val EXPIRATION_TIMER_UPDATE_BIT: Long = 0x40000
  private const val GROUP_V2_BIT: Long = 0x80000
  private const val GROUP_V2_LEAVE_BITS = GROUP_V2_BIT or GROUP_LEAVE_BIT or GROUP_UPDATE_BIT
  private const val ENCRYPTION_REMOTE_BIT: Long = 0x20000000
  private const val ENCRYPTION_REMOTE_FAILED_BIT: Long = 0x10000000
  private const val ENCRYPTION_REMOTE_NO_SESSION_BIT: Long = 0x08000000
  private const val ENCRYPTION_REMOTE_DUPLICATE_BIT: Long = 0x04000000
  private const val ENCRYPTION_REMOTE_LEGACY_BIT: Long = 0x02000000

  fun String.isMessageType(): Boolean {
    return this == "type" || this == "msg_box"
  }

  private fun isOutgoingMessageType(type: Long): Boolean {
    for (outgoingType in OUTGOING_MESSAGE_TYPES) {
      if (type and BASE_TYPE_MASK == outgoingType) return true
    }
    return false
  }

  fun describeMessageType(type: Long): String {
    val describe = """
      isOutgoingMessageType:${isOutgoingMessageType(type)}
      isForcedSms:${type and MESSAGE_FORCE_SMS_BIT != 0L}
      isDraftMessageType:${type and BASE_TYPE_MASK == BASE_DRAFT_TYPE}
      isFailedMessageType:${type and BASE_TYPE_MASK == BASE_SENT_FAILED_TYPE}
      isPendingMessageType:${type and BASE_TYPE_MASK == BASE_OUTBOX_TYPE || type and BASE_TYPE_MASK == outgoingSmsMessageType}
      isSentType:${type and BASE_TYPE_MASK == BASE_SENT_TYPE}
      isPendingSmsFallbackType:${type and BASE_TYPE_MASK == BASE_PENDING_INSECURE_SMS_FALLBACK || type and BASE_TYPE_MASK == BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingSecureSmsFallbackType:${type and BASE_TYPE_MASK == BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingInsecureSmsFallbackType:${type and BASE_TYPE_MASK == BASE_PENDING_INSECURE_SMS_FALLBACK}
      isInboxType:${type and BASE_TYPE_MASK == BASE_INBOX_TYPE}
      isJoinedType:${type and BASE_TYPE_MASK == JOINED_TYPE}
      isUnsupportedMessageType:${type and BASE_TYPE_MASK == UNSUPPORTED_MESSAGE_TYPE}
      isInvalidMessageType:${type and BASE_TYPE_MASK == INVALID_MESSAGE_TYPE}
      isBadDecryptType:${type and BASE_TYPE_MASK == BAD_DECRYPT_TYPE}
      isSecureType:${type and SECURE_MESSAGE_BIT != 0L}
      isPushType:${type and PUSH_MESSAGE_BIT != 0L}
      isEndSessionType:${type and END_SESSION_BIT != 0L}
      isKeyExchangeType:${type and KEY_EXCHANGE_BIT != 0L}
      isIdentityVerified:${type and KEY_EXCHANGE_IDENTITY_VERIFIED_BIT != 0L}
      isIdentityDefault:${type and KEY_EXCHANGE_IDENTITY_DEFAULT_BIT != 0L}
      isCorruptedKeyExchange:${type and KEY_EXCHANGE_CORRUPTED_BIT != 0L}
      isInvalidVersionKeyExchange:${type and KEY_EXCHANGE_INVALID_VERSION_BIT != 0L}
      isBundleKeyExchange:${type and KEY_EXCHANGE_BUNDLE_BIT != 0L}
      isContentBundleKeyExchange:${type and KEY_EXCHANGE_CONTENT_FORMAT != 0L}
      isIdentityUpdate:${type and KEY_EXCHANGE_IDENTITY_UPDATE_BIT != 0L}
      isRateLimited:${type and MESSAGE_RATE_LIMITED_BIT != 0L}
      isExpirationTimerUpdate:${type and EXPIRATION_TIMER_UPDATE_BIT != 0L}
      isIncomingAudioCall:${type == INCOMING_AUDIO_CALL_TYPE}
      isIncomingVideoCall:${type == INCOMING_VIDEO_CALL_TYPE}
      isOutgoingAudioCall:${type == OUTGOING_AUDIO_CALL_TYPE}
      isOutgoingVideoCall:${type == OUTGOING_VIDEO_CALL_TYPE}
      isMissedAudioCall:${type == MISSED_AUDIO_CALL_TYPE}
      isMissedVideoCall:${type == MISSED_VIDEO_CALL_TYPE}
      isGroupCall:${type == GROUP_CALL_TYPE}
      isGroupUpdate:${type and GROUP_UPDATE_BIT != 0L}
      isGroupV2:${type and GROUP_V2_BIT != 0L}
      isGroupQuit:${type and GROUP_LEAVE_BIT != 0L && type and GROUP_V2_BIT == 0L}
      isChatSessionRefresh:${type and ENCRYPTION_REMOTE_FAILED_BIT != 0L}
      isDuplicateMessageType:${type and ENCRYPTION_REMOTE_DUPLICATE_BIT != 0L}
      isDecryptInProgressType:${type and 0x40000000 != 0L}
      isNoRemoteSessionType:${type and ENCRYPTION_REMOTE_NO_SESSION_BIT != 0L}
      isLegacyType:${type and ENCRYPTION_REMOTE_LEGACY_BIT != 0L || type and ENCRYPTION_REMOTE_BIT != 0L}
      isProfileChange:${type == PROFILE_CHANGE_TYPE}
      isGroupV1MigrationEvent:${type == GV1_MIGRATION_TYPE}
      isChangeNumber:${type == CHANGE_NUMBER_TYPE}
      isBoostRequest:${type == BOOST_REQUEST_TYPE}
      isGroupV2LeaveOnly:${type and GROUP_V2_LEAVE_BITS == GROUP_V2_LEAVE_BITS}
    """.trimIndent()

    return describe.replace(Regex("is[A-Z][A-Za-z0-9]*:false\n?"), "").replace("\n", "<br>")
  }
}
