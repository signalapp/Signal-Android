package org.whispersystems.textsecure.crypto.protocol;

public interface CiphertextMessage {

  public static final int LEGACY_VERSION     = 1;
  public static final int CURRENT_VERSION    = 2;

  public static final int LEGACY_WHISPER_TYPE  = 1;
  public static final int CURRENT_WHISPER_TYPE = 2;
  public static final int PREKEY_WHISPER_TYPE  = 3;

  // This should be the worst case (worse than V2).  So not always accurate, but good enough for padding.
  public static final int ENCRYPTED_MESSAGE_OVERHEAD = WhisperMessageV1.ENCRYPTED_MESSAGE_OVERHEAD;

  public byte[] serialize();
  public int getType();

}