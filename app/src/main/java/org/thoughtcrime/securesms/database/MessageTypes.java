package org.thoughtcrime.securesms.database;

/**
 * For storage efficiency, all types are stored within a single 64-bit integer column in the
 * database. There are various areas reserved for different classes of data.
 * <p>
 * When carving out a new area, if it's storing a bunch of mutually-exclusive flags (like in
 * {@link #BASE_TYPE_MASK}, you should store integers in that area. If multiple flags can be set
 * within a category, you'll have to store them as bits. Just keep in mind that storing as bits
 * means we can store less data (i.e. 4 bits can store 16 exclusive values, or 4 non-exclusive
 * values). This was not always followed in the past, and now we've wasted some space.
 * <p>
 * Note: We technically could use up to 64 bits, but {@link #TOTAL_MASK} is currently just set to
 * look at 32. Theoretically if we needed more bits, we could just use them and expand the size of
 * {@link #TOTAL_MASK}.
 *
 * <pre>
 *    ____________________________________________ SPECIAL TYPES ({@link #SPECIAL_TYPES_MASK}
 *   |       _____________________________________ ENCRYPTION ({@link #ENCRYPTION_MASK})
 *   |      |        _____________________________ SECURE MESSAGE INFORMATION (no mask, but look at {@link #SECURE_MESSAGE_BIT})
 *   |      |       |     ________________________ GROUPS (no mask, but look at {@link #GROUP_UPDATE_BIT})
 *   |      |       |    |       _________________ KEY_EXCHANGE ({@link #KEY_EXCHANGE_MASK})
 *   |      |       |    |      |       _________  MESSAGE_ATTRIBUTES ({@link #MESSAGE_ATTRIBUTE_MASK})
 *   |      |       |    |      |      |     ____  BASE_TYPE ({@link #BASE_TYPE_MASK})
 *  _|   ___|___   _|   _|   ___|__    |  __|_
 * |  | |       | |  | |  | |       | | ||    |
 * 0000 0000 0000 0000 0000 0000 0000 0000 0000
 * </pre>
 */
public interface MessageTypes {
  long TOTAL_MASK = 0xFFFFFFFFFL;

  // Base Types
  long BASE_TYPE_MASK = 0x1F;

  long INCOMING_AUDIO_CALL_TYPE = 1;
  long OUTGOING_AUDIO_CALL_TYPE = 2;
  long MISSED_AUDIO_CALL_TYPE   = 3;
  long JOINED_TYPE              = 4;
  long UNSUPPORTED_MESSAGE_TYPE = 5;
  long INVALID_MESSAGE_TYPE     = 6;
  long PROFILE_CHANGE_TYPE      = 7;
  long MISSED_VIDEO_CALL_TYPE   = 8;
  long GV1_MIGRATION_TYPE       = 9;
  long INCOMING_VIDEO_CALL_TYPE = 10;
  long OUTGOING_VIDEO_CALL_TYPE = 11;
  long GROUP_CALL_TYPE          = 12;
  long BAD_DECRYPT_TYPE         = 13;
  long CHANGE_NUMBER_TYPE       = 14;
  long BOOST_REQUEST_TYPE       = 15;
  long THREAD_MERGE_TYPE        = 16;
  long SMS_EXPORT_TYPE          = 17;
  long SESSION_SWITCHOVER_TYPE  = 18;

  long BASE_INBOX_TYPE                    = 20;
  long BASE_OUTBOX_TYPE                   = 21;
  long BASE_SENDING_TYPE                  = 22;
  long BASE_SENT_TYPE                     = 23;
  long BASE_SENT_FAILED_TYPE              = 24;
  long BASE_PENDING_SECURE_SMS_FALLBACK   = 25;
  long BASE_PENDING_INSECURE_SMS_FALLBACK = 26;
  long BASE_DRAFT_TYPE = 27;

  long[] OUTGOING_MESSAGE_TYPES = { BASE_OUTBOX_TYPE, BASE_SENT_TYPE,
                                    BASE_SENDING_TYPE, BASE_SENT_FAILED_TYPE,
                                    BASE_PENDING_SECURE_SMS_FALLBACK,
                                    BASE_PENDING_INSECURE_SMS_FALLBACK,
                                    OUTGOING_AUDIO_CALL_TYPE, OUTGOING_VIDEO_CALL_TYPE };

  // Message attributes
  long MESSAGE_ATTRIBUTE_MASK   = 0xE0;
  long MESSAGE_RATE_LIMITED_BIT = 0x80;
  long MESSAGE_FORCE_SMS_BIT    = 0x40;
  // Note: Might be wise to reserve 0x20 -- it would let us expand BASE_MASK by a bit if needed

  // Key Exchange Information
  long KEY_EXCHANGE_MASK                  = 0xFF00;
  long KEY_EXCHANGE_BIT                   = 0x8000;
  long KEY_EXCHANGE_IDENTITY_VERIFIED_BIT = 0x4000;
  long KEY_EXCHANGE_IDENTITY_DEFAULT_BIT  = 0x2000;
  long KEY_EXCHANGE_CORRUPTED_BIT         = 0x1000;
  long KEY_EXCHANGE_INVALID_VERSION_BIT   = 0x800;
  long KEY_EXCHANGE_BUNDLE_BIT            = 0x400;
  long KEY_EXCHANGE_IDENTITY_UPDATE_BIT   = 0x200;
  long KEY_EXCHANGE_CONTENT_FORMAT        = 0x100;

  // Secure Message Information
  long SECURE_MESSAGE_BIT = 0x800000;
  long END_SESSION_BIT    = 0x400000;
  long PUSH_MESSAGE_BIT   = 0x200000;

  // Group Message Information
  long GROUP_UPDATE_BIT            = 0x10000;
  // Note: Leave bit was previous QUIT bit for GV1, now also general member leave for GV2
  long GROUP_LEAVE_BIT             = 0x20000;
  long EXPIRATION_TIMER_UPDATE_BIT = 0x40000;
  long GROUP_V2_BIT                = 0x80000;
  long GROUP_V2_LEAVE_BITS         = GROUP_V2_BIT | GROUP_LEAVE_BIT | GROUP_UPDATE_BIT;

  // Encrypted Storage Information XXX
  long ENCRYPTION_MASK                  = 0xFF000000;
//  long ENCRYPTION_SYMMETRIC_BIT         = 0x80000000; Deprecated
//  long ENCRYPTION_ASYMMETRIC_BIT        = 0x40000000; Deprecated
  long ENCRYPTION_REMOTE_BIT            = 0x20000000;
  long ENCRYPTION_REMOTE_FAILED_BIT     = 0x10000000;
  long ENCRYPTION_REMOTE_NO_SESSION_BIT = 0x08000000;
  long ENCRYPTION_REMOTE_DUPLICATE_BIT  = 0x04000000;
  long ENCRYPTION_REMOTE_LEGACY_BIT     = 0x02000000;

  // Special message types
  long SPECIAL_TYPES_MASK                     = 0xF00000000L;
  long SPECIAL_TYPE_STORY_REACTION            = 0x100000000L;
  long SPECIAL_TYPE_GIFT_BADGE                = 0x200000000L;
  long SPECIAL_TYPE_PAYMENTS_NOTIFICATION     = 0x300000000L;
  long SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST = 0x400000000L;
  long SPECIAL_TYPE_PAYMENTS_ACTIVATED        = 0x800000000L;

  long IGNORABLE_TYPESMASK_WHEN_COUNTING = END_SESSION_BIT | KEY_EXCHANGE_IDENTITY_UPDATE_BIT | KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;

  static boolean isStoryReaction(long type) {
    return (type & SPECIAL_TYPES_MASK) == SPECIAL_TYPE_STORY_REACTION;
  }

  static boolean isGiftBadge(long type) {
    return (type & SPECIAL_TYPES_MASK) == SPECIAL_TYPE_GIFT_BADGE;
  }

  static boolean isPaymentsNotification(long type) {
    return (type & SPECIAL_TYPES_MASK) == SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
  }

  static boolean isPaymentsRequestToActivate(long type) {
    return (type & SPECIAL_TYPES_MASK) == SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
  }

  static boolean isPaymentsActivated(long type) {
    return (type & SPECIAL_TYPES_MASK) == SPECIAL_TYPE_PAYMENTS_ACTIVATED;
  }

  static boolean isDraftMessageType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_DRAFT_TYPE;
  }

  static boolean isFailedMessageType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_SENT_FAILED_TYPE;
  }

  static boolean isOutgoingMessageType(long type) {
    for (long outgoingType : OUTGOING_MESSAGE_TYPES) {
      if ((type & BASE_TYPE_MASK) == outgoingType)
        return true;
    }

    return false;
  }

  static long getOutgoingEncryptedMessageType() {
    return BASE_SENDING_TYPE | SECURE_MESSAGE_BIT | PUSH_MESSAGE_BIT;
  }

  static long getOutgoingSmsMessageType() {
    return BASE_SENDING_TYPE;
  }

  static boolean isForcedSms(long type) {
    return (type & MESSAGE_FORCE_SMS_BIT) != 0;
  }

  static boolean isPendingMessageType(long type) {
    return
        (type & BASE_TYPE_MASK) == BASE_OUTBOX_TYPE ||
        (type & BASE_TYPE_MASK) == BASE_SENDING_TYPE;
  }

  static boolean isSentType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_SENT_TYPE;
  }

  static boolean isPendingSecureSmsFallbackType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_PENDING_SECURE_SMS_FALLBACK;
  }

  static boolean isPendingInsecureSmsFallbackType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_PENDING_INSECURE_SMS_FALLBACK;
  }

  static boolean isInboxType(long type) {
    return (type & BASE_TYPE_MASK) == BASE_INBOX_TYPE;
  }

  static boolean isJoinedType(long type) {
    return (type & BASE_TYPE_MASK) == JOINED_TYPE;
  }

  static boolean isUnsupportedMessageType(long type) {
    return (type & BASE_TYPE_MASK) == UNSUPPORTED_MESSAGE_TYPE;
  }

  static boolean isInvalidMessageType(long type) {
    return (type & BASE_TYPE_MASK) == INVALID_MESSAGE_TYPE;
  }

  static boolean isBadDecryptType(long type) {
    return (type & BASE_TYPE_MASK) == BAD_DECRYPT_TYPE;
  }

  static boolean isThreadMergeType(long type) {
    return (type & BASE_TYPE_MASK) == THREAD_MERGE_TYPE;
  }

  static boolean isSessionSwitchoverType(long type) {
    return (type & BASE_TYPE_MASK) == SESSION_SWITCHOVER_TYPE;
  }

  static boolean isSecureType(long type) {
    return (type & SECURE_MESSAGE_BIT) != 0;
  }

  static boolean isPushType(long type) {
    return (type & PUSH_MESSAGE_BIT) != 0;
  }

  static boolean isEndSessionType(long type) {
    return (type & END_SESSION_BIT) != 0;
  }

  static boolean isKeyExchangeType(long type) {
    return (type & KEY_EXCHANGE_BIT) != 0;
  }

  static boolean isIdentityVerified(long type) {
    return (type & KEY_EXCHANGE_IDENTITY_VERIFIED_BIT) != 0;
  }

  static boolean isIdentityDefault(long type) {
    return (type & KEY_EXCHANGE_IDENTITY_DEFAULT_BIT) != 0;
  }

  static boolean isCorruptedKeyExchange(long type) {
    return (type & KEY_EXCHANGE_CORRUPTED_BIT) != 0;
  }

  static boolean isInvalidVersionKeyExchange(long type) {
    return (type & KEY_EXCHANGE_INVALID_VERSION_BIT) != 0;
  }

  static boolean isBundleKeyExchange(long type) {
    return (type & KEY_EXCHANGE_BUNDLE_BIT) != 0;
  }

  static boolean isContentBundleKeyExchange(long type) {
    return (type & KEY_EXCHANGE_CONTENT_FORMAT) != 0;
  }

  static boolean isIdentityUpdate(long type) {
    return (type & KEY_EXCHANGE_IDENTITY_UPDATE_BIT) != 0;
  }

  static boolean isRateLimited(long type) {
    return (type & MESSAGE_RATE_LIMITED_BIT) != 0;
  }

  static boolean isCallLog(long type) {
    return isIncomingAudioCall(type) ||
           isIncomingVideoCall(type) ||
           isOutgoingAudioCall(type) ||
           isOutgoingVideoCall(type) ||
           isMissedAudioCall(type) ||
           isMissedVideoCall(type) ||
           isGroupCall(type);
  }

  static boolean isExpirationTimerUpdate(long type) {
    return (type & EXPIRATION_TIMER_UPDATE_BIT) != 0;
  }

  static boolean isIncomingAudioCall(long type) {
    return type == INCOMING_AUDIO_CALL_TYPE;
  }

  static boolean isIncomingVideoCall(long type) {
    return type == INCOMING_VIDEO_CALL_TYPE;
  }

  static boolean isOutgoingAudioCall(long type) {
    return type == OUTGOING_AUDIO_CALL_TYPE;
  }

  static boolean isOutgoingVideoCall(long type) {
    return type == OUTGOING_VIDEO_CALL_TYPE;
  }

  static boolean isMissedAudioCall(long type) {
    return type == MISSED_AUDIO_CALL_TYPE;
  }

  static boolean isMissedVideoCall(long type) {
    return type == MISSED_VIDEO_CALL_TYPE;
  }

  static boolean isGroupCall(long type) {
    return type == GROUP_CALL_TYPE;
  }

  static boolean isGroupUpdate(long type) {
    return (type & GROUP_UPDATE_BIT) != 0;
  }

  static boolean isGroupV2(long type) {
    return (type & GROUP_V2_BIT) != 0;
  }

  static boolean isGroupQuit(long type) {
    return (type & GROUP_LEAVE_BIT) != 0 && (type & GROUP_V2_BIT) == 0;
  }

  static boolean isChatSessionRefresh(long type) {
    return (type & ENCRYPTION_REMOTE_FAILED_BIT) != 0;
  }

  static boolean isDuplicateMessageType(long type) {
    return (type & ENCRYPTION_REMOTE_DUPLICATE_BIT) != 0;
  }

  static boolean isNoRemoteSessionType(long type) {
    return (type & ENCRYPTION_REMOTE_NO_SESSION_BIT) != 0;
  }

  static boolean isLegacyType(long type) {
    return (type & ENCRYPTION_REMOTE_LEGACY_BIT) != 0 ||
           (type & ENCRYPTION_REMOTE_BIT) != 0;
  }

  static boolean isProfileChange(long type) {
    return type == PROFILE_CHANGE_TYPE;
  }

  static boolean isGroupV1MigrationEvent(long type) {
    return type == GV1_MIGRATION_TYPE;
  }

  static boolean isChangeNumber(long type) {
    return type == CHANGE_NUMBER_TYPE;
  }

  static boolean isBoostRequest(long type) {
    return type == BOOST_REQUEST_TYPE;
  }

  static boolean isSmsExport(long type) {
    return type == SMS_EXPORT_TYPE;
  }

  static boolean isGroupV2LeaveOnly(long type) {
    return (type & GROUP_V2_LEAVE_BITS) == GROUP_V2_LEAVE_BITS;
  }
}
