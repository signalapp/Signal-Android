package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * Helper methods for interacting with [MessageTable] in tests.
 */
object MessageTableTestUtils {

  fun getMessages(threadId: Long): List<MessageRecord> {
    return MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId)).use {
      it.toList()
    }
  }

  fun typeColumnToString(type: Long): String {
    return """
      isOutgoingMessageType:${MessageTypes.isOutgoingMessageType(type)}
      isForcedSms:${type and MessageTypes.MESSAGE_FORCE_SMS_BIT != 0L}
      isDraftMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_DRAFT_TYPE}
      isFailedMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENT_FAILED_TYPE}
      isPendingMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_OUTBOX_TYPE || type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENDING_TYPE}
      isSentType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENT_TYPE}
      isPendingSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK || type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingSecureSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingInsecureSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK}
      isInboxType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_INBOX_TYPE}
      isJoinedType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.JOINED_TYPE}
      isUnsupportedMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.UNSUPPORTED_MESSAGE_TYPE}
      isInvalidMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.INVALID_MESSAGE_TYPE}
      isBadDecryptType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BAD_DECRYPT_TYPE}
      isSecureType:${type and MessageTypes.SECURE_MESSAGE_BIT != 0L}
      isPushType:${type and MessageTypes.PUSH_MESSAGE_BIT != 0L}
      isEndSessionType:${type and MessageTypes.END_SESSION_BIT != 0L}
      isKeyExchangeType:${type and MessageTypes.KEY_EXCHANGE_BIT != 0L}
      isIdentityVerified:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT != 0L}
      isIdentityDefault:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT != 0L}
      isInvalidVersionKeyExchange:${type and MessageTypes.KEY_EXCHANGE_INVALID_VERSION_BIT != 0L}
      isBundleKeyExchange:${type and MessageTypes.KEY_EXCHANGE_BUNDLE_BIT != 0L}
      isIdentityUpdate:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT != 0L}
      isRateLimited:${type and MessageTypes.MESSAGE_RATE_LIMITED_BIT != 0L}
      isExpirationTimerUpdate:${type and MessageTypes.EXPIRATION_TIMER_UPDATE_BIT != 0L}
      isIncomingAudioCall:${type == MessageTypes.INCOMING_AUDIO_CALL_TYPE}
      isIncomingVideoCall:${type == MessageTypes.INCOMING_VIDEO_CALL_TYPE}
      isOutgoingAudioCall:${type == MessageTypes.OUTGOING_AUDIO_CALL_TYPE}
      isOutgoingVideoCall:${type == MessageTypes.OUTGOING_VIDEO_CALL_TYPE}
      isMissedAudioCall:${type == MessageTypes.MISSED_AUDIO_CALL_TYPE}
      isMissedVideoCall:${type == MessageTypes.MISSED_VIDEO_CALL_TYPE}
      isGroupCall:${type == MessageTypes.GROUP_CALL_TYPE}
      isGroupUpdate:${type and MessageTypes.GROUP_UPDATE_BIT != 0L}
      isGroupV2:${type and MessageTypes.GROUP_V2_BIT != 0L}
      isGroupQuit:${type and MessageTypes.GROUP_LEAVE_BIT != 0L && type and MessageTypes.GROUP_V2_BIT == 0L}
      isChatSessionRefresh:${type and MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT != 0L}
      isDuplicateMessageType:${type and MessageTypes.ENCRYPTION_REMOTE_DUPLICATE_BIT != 0L}
      isDecryptInProgressType:${type and 0x40000000 != 0L}
      isNoRemoteSessionType:${type and MessageTypes.ENCRYPTION_REMOTE_NO_SESSION_BIT != 0L}
      isLegacyType:${type and MessageTypes.ENCRYPTION_REMOTE_LEGACY_BIT != 0L || type and MessageTypes.ENCRYPTION_REMOTE_BIT != 0L}
      isProfileChange:${type == MessageTypes.PROFILE_CHANGE_TYPE}
      isGroupV1MigrationEvent:${type == MessageTypes.GV1_MIGRATION_TYPE}
      isChangeNumber:${type == MessageTypes.CHANGE_NUMBER_TYPE}
      isDonationChannelDonationRequest:${type == MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE}
      isThreadMerge:${type == MessageTypes.THREAD_MERGE_TYPE}
      isSmsExport:${type == MessageTypes.SMS_EXPORT_TYPE}
      isGroupV2LeaveOnly:${type and MessageTypes.GROUP_V2_LEAVE_BITS == MessageTypes.GROUP_V2_LEAVE_BITS}
      isSpecialType:${type and MessageTypes.SPECIAL_TYPES_MASK != 0L}
      isStoryReaction:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_STORY_REACTION}
      isGiftBadge:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_GIFT_BADGE}
      isPaymentsNotificaiton:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION}
      isRequestToActivatePayments:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST}
      isPaymentsActivated:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED}
    """.trimIndent().replace(Regex("is[A-Z][A-Za-z0-9]*:false\n?"), "").replace("\n", "")
  }
}
