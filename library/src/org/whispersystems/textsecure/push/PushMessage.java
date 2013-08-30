package org.whispersystems.textsecure.push;


public interface PushMessage {
  public static final int TYPE_MESSAGE_PLAINTEXT     = 0;
  public static final int TYPE_MESSAGE_CIPHERTEXT    = 1;
  public static final int TYPE_MESSAGE_KEY_EXCHANGE  = 2;
  public static final int TYPE_MESSAGE_PREKEY_BUNDLE = 3;
}
