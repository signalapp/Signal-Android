package org.thoughtcrime.securesms.database;

public interface MmsSmsColumns {

  public static final String ID                       = "_id";
  public static final String NORMALIZED_DATE_SENT     = "date_sent";
  public static final String NORMALIZED_DATE_RECEIVED = "date_received";
  public static final String THREAD_ID                = "thread_id";
  public static final String READ                     = "read";
  public static final String BODY                     = "body";
  public  static final String ADDRESS                 = "address";


  public static class Types {
    protected static final long TOTAL_MASK = 0xFFFFFFFF;

    // Base Types
    protected static final long BASE_TYPE_MASK        = 0xFF;

    protected static final long BASE_INBOX_TYPE       = 20;
    protected static final long BASE_OUTBOX_TYPE      = 21;
    protected static final long BASE_SENDING_TYPE     = 22;
    protected static final long BASE_SENT_TYPE        = 23;
    protected static final long BASE_SENT_FAILED_TYPE = 24;

    protected static final long[] OUTGOING_MESSAGE_TYPES = {BASE_OUTBOX_TYPE, BASE_SENT_TYPE,
                                                            BASE_SENDING_TYPE, BASE_SENT_FAILED_TYPE};

    // Key Exchange Information
    protected static final long KEY_EXCHANGE_BIT                 = 0x8000;
    protected static final long KEY_EXCHANGE_STALE_BIT           = 0x4000;
    protected static final long KEY_EXCHANGE_PROCESSED_BIT       = 0x2000;
    protected static final long KEY_EXCHANGE_CORRUPTED_BIT       = 0x1000;
    protected static final long KEY_EXCHANGE_INVALID_VERSION_BIT =  0x800;

    // Secure Message Information
    protected static final long SECURE_MESSAGE_BIT = 0x800000;

    // Encrypted Storage Information
    protected static final long ENCRYPTION_MASK                  = 0xFF000000;
    protected static final long ENCRYPTION_SYMMETRIC_BIT         = 0x80000000;
    protected static final long ENCRYPTION_ASYMMETRIC_BIT        = 0x40000000;
    protected static final long ENCRYPTION_REMOTE_BIT            = 0x20000000;
    protected static final long ENCRYPTION_REMOTE_FAILED_BIT     = 0x10000000;
    protected static final long ENCRYPTION_REMOTE_NO_SESSION_BIT = 0x08000000;

    public static boolean isFailedMessageType(long type) {
      return (type & BASE_TYPE_MASK) == BASE_SENT_FAILED_TYPE;
    }

    public static boolean isOutgoingMessageType(long type) {
      for (long outgoingType : OUTGOING_MESSAGE_TYPES) {
        if ((type & BASE_TYPE_MASK) == outgoingType)
          return true;
      }

      return false;
    }

    public static boolean isPendingMessageType(long type) {
      return
          (type & BASE_TYPE_MASK) == BASE_OUTBOX_TYPE ||
              (type & BASE_TYPE_MASK) == BASE_SENDING_TYPE;
    }

    public static boolean isInboxType(long type) {
      return (type & BASE_TYPE_MASK) == BASE_INBOX_TYPE;
    }

    public static boolean isSecureType(long type) {
      return (type & SECURE_MESSAGE_BIT) != 0;
    }

    public static boolean isKeyExchangeType(long type) {
      return (type & KEY_EXCHANGE_BIT) != 0;
    }

    public static boolean isStaleKeyExchange(long type) {
      return (type & KEY_EXCHANGE_STALE_BIT) != 0;
    }

    public static boolean isProcessedKeyExchange(long type) {
      return (type & KEY_EXCHANGE_PROCESSED_BIT) != 0;
    }

    public static boolean isCorruptedKeyExchange(long type) {
      return (type & KEY_EXCHANGE_CORRUPTED_BIT) != 0;
    }

    public static boolean isInvalidVersionKeyExchange(long type) {
      return (type & KEY_EXCHANGE_INVALID_VERSION_BIT) != 0;
    }

    public static boolean isSymmetricEncryption(long type) {
      return (type & ENCRYPTION_SYMMETRIC_BIT) != 0;
    }

    public static boolean isFailedDecryptType(long type) {
      return (type & ENCRYPTION_REMOTE_FAILED_BIT) != 0;
    }

    public static boolean isDecryptInProgressType(long type) {
      return
          (type & ENCRYPTION_REMOTE_BIT)     != 0 ||
          (type & ENCRYPTION_ASYMMETRIC_BIT) != 0;
    }

    public static boolean isNoRemoteSessionType(long type) {
      return (type & ENCRYPTION_REMOTE_NO_SESSION_BIT) != 0;
    }

    public static long translateFromSystemBaseType(long theirType) {
//    public static final int NONE_TYPE           = 0;
//    public static final int INBOX_TYPE          = 1;
//    public static final int SENT_TYPE           = 2;
//    public static final int SENT_PENDING        = 4;
//    public static final int FAILED_TYPE         = 5;

      switch ((int)theirType) {
        case 1: return BASE_INBOX_TYPE;
        case 2: return BASE_SENT_TYPE;
        case 4: return BASE_OUTBOX_TYPE;
        case 5: return BASE_SENT_FAILED_TYPE;
      }

      return BASE_INBOX_TYPE;
    }

    public static int translateToSystemBaseType(long type) {
      if      (isInboxType(type))           return 1;
      else if (isOutgoingMessageType(type)) return 2;
      else if (isFailedMessageType(type))   return 5;

      return 1;
    }


//
//
//
//    public static final int NONE_TYPE           = 0;
//    public static final int INBOX_TYPE          = 1;
//    public static final int SENT_TYPE           = 2;
//    public static final int SENT_PENDING        = 4;
//    public static final int FAILED_TYPE         = 5;
//
//    public static final int OUTBOX_TYPE = 43;  // Messages are stored local encrypted and need delivery.
//
//
//    public static final int ENCRYPTING_TYPE      = 42;  // Messages are stored local encrypted and need async encryption and delivery.
//    public static final int SECURE_SENT_TYPE     = 44;  // Messages were sent with async encryption.
//    public static final int SECURE_RECEIVED_TYPE = 45;  // Messages were received with async decryption.
//    public static final int FAILED_DECRYPT_TYPE  = 46;  // Messages were received with async encryption and failed to decrypt.
//    public static final int DECRYPTING_TYPE      = 47;  // Messages are in the process of being asymmetricaly decrypted.
//    public static final int NO_SESSION_TYPE      = 48;  // Messages were received with async encryption but there is no session yet.
//
//    public static final int OUTGOING_KEY_EXCHANGE_TYPE  = 49;
//    public static final int INCOMING_KEY_EXCHANGE_TYPE  = 50;
//    public static final int STALE_KEY_EXCHANGE_TYPE     = 51;
//    public static final int PROCESSED_KEY_EXCHANGE_TYPE = 52;
//
//    public static final int[] OUTGOING_MESSAGE_TYPES = {SENT_TYPE, SENT_PENDING, ENCRYPTING_TYPE,
//                                                        OUTBOX_TYPE, SECURE_SENT_TYPE,
//                                                        FAILED_TYPE, OUTGOING_KEY_EXCHANGE_TYPE};
//
//    public static boolean isFailedMessageType(long type) {
//      return type == FAILED_TYPE;
//    }
//
//    public static boolean isOutgoingMessageType(long type) {
//      for (int outgoingType : OUTGOING_MESSAGE_TYPES) {
//        if (type == outgoingType)
//          return true;
//      }
//
//      return false;
//    }
//
//    public static boolean isPendingMessageType(long type) {
//      return type == SENT_PENDING || type == ENCRYPTING_TYPE || type == OUTBOX_TYPE;
//    }
//
//    public static boolean isSecureType(long type) {
//      return
//          type == SECURE_SENT_TYPE     || type == ENCRYPTING_TYPE ||
//          type == SECURE_RECEIVED_TYPE || type == DECRYPTING_TYPE;
//    }
//
//    public static boolean isKeyExchangeType(long type) {
//      return type == OUTGOING_KEY_EXCHANGE_TYPE || type == INCOMING_KEY_EXCHANGE_TYPE;
//    }
  }


}
