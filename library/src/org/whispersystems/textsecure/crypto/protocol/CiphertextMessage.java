package org.whispersystems.textsecure.crypto.protocol;

public interface CiphertextMessage {

  public static final int UNSUPPORTED_VERSION = 1;
  public static final int CURRENT_VERSION     = 2;

  public static final int WHISPER_TYPE = 2;
  public static final int PREKEY_TYPE  = 3;

  // This should be the worst case (worse than V2).  So not always accurate, but good enough for padding.
  public static final int ENCRYPTED_MESSAGE_OVERHEAD = 53;

  public byte[] serialize();
  public int getType();

}