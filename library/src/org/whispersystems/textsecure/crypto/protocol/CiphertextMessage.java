package org.whispersystems.textsecure.crypto.protocol;

import org.whispersystems.textsecure.crypto.InvalidMacException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MessageMac;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.util.Conversions;

public class CiphertextMessage {

  public static final int SUPPORTED_VERSION       = 2;
  public static final int DHE3_INTRODUCED_VERSION = 2;

          static final int VERSION_LENGTH         = 1;
  private static final int SENDER_KEY_ID_LENGTH   = 3;
  private static final int RECEIVER_KEY_ID_LENGTH = 3;
  private static final int NEXT_KEY_LENGTH        = PublicKey.KEY_SIZE;
  private static final int COUNTER_LENGTH         = 3;
  private static final int HEADER_LENGTH          =  VERSION_LENGTH + SENDER_KEY_ID_LENGTH   +
                                                     RECEIVER_KEY_ID_LENGTH + COUNTER_LENGTH +
                                                     NEXT_KEY_LENGTH;

          static final int VERSION_OFFSET         = 0;
  private static final int SENDER_KEY_ID_OFFSET   = VERSION_OFFSET + VERSION_LENGTH;
  private static final int RECEIVER_KEY_ID_OFFSET = SENDER_KEY_ID_OFFSET + SENDER_KEY_ID_LENGTH;
  private static final int NEXT_KEY_OFFSET        = RECEIVER_KEY_ID_OFFSET + RECEIVER_KEY_ID_LENGTH;
  private static final int COUNTER_OFFSET         = NEXT_KEY_OFFSET + NEXT_KEY_LENGTH;
  private static final int BODY_OFFSET            = COUNTER_OFFSET + COUNTER_LENGTH;

  public static final int ENCRYPTED_MESSAGE_OVERHEAD = HEADER_LENGTH + MessageMac.MAC_LENGTH;

  private final byte[] ciphertext;

  public CiphertextMessage(SessionCipher.SessionCipherContext sessionContext, byte[] ciphertextBody) {
    this.ciphertext = new byte[HEADER_LENGTH + ciphertextBody.length + MessageMac.MAC_LENGTH];
    setVersion(sessionContext.getMessageVersion(), SUPPORTED_VERSION);
    setSenderKeyId(sessionContext.getSenderKeyId());
    setReceiverKeyId(sessionContext.getRecipientKeyId());
    setNextKeyBytes(sessionContext.getNextKey().serialize());
    setCounter(sessionContext.getCounter());
    setBody(ciphertextBody);
    setMac(MessageMac.calculateMac(ciphertext, 0, ciphertext.length - MessageMac.MAC_LENGTH,
                                   sessionContext.getSessionKey().getMacKey()));
  }

  public CiphertextMessage(byte[] ciphertext) throws InvalidMessageException {
    this.ciphertext = ciphertext;

    if (ciphertext.length < HEADER_LENGTH) {
      throw new InvalidMessageException("Not long enough for ciphertext header!");
    }

    if (getCurrentVersion() > SUPPORTED_VERSION) {
      throw new InvalidMessageException("Unspported version: " + getCurrentVersion());
    }
  }

  public void setVersion(int current, int supported) {
    ciphertext[VERSION_OFFSET] = Conversions.intsToByteHighAndLow(current, supported);
  }

  public int getCurrentVersion() {
    return Conversions.highBitsToInt(ciphertext[VERSION_OFFSET]);
  }

  public int getSupportedVersion() {
    return Conversions.lowBitsToInt(ciphertext[VERSION_OFFSET]);
  }

  public void setSenderKeyId(int senderKeyId) {
    Conversions.mediumToByteArray(ciphertext, SENDER_KEY_ID_OFFSET, senderKeyId);
  }

  public int getSenderKeyId() {
    return Conversions.byteArrayToMedium(ciphertext, SENDER_KEY_ID_OFFSET);
  }

  public void setReceiverKeyId(int receiverKeyId) {
    Conversions.mediumToByteArray(ciphertext, RECEIVER_KEY_ID_OFFSET, receiverKeyId);
  }

  public int getReceiverKeyId() {
    return Conversions.byteArrayToMedium(ciphertext, RECEIVER_KEY_ID_OFFSET);
  }

  public void setNextKeyBytes(byte[] nextKey) {
    assert(nextKey.length == NEXT_KEY_LENGTH);
    System.arraycopy(nextKey, 0, ciphertext, NEXT_KEY_OFFSET, nextKey.length);
  }

  public byte[] getNextKeyBytes() {
    byte[] nextKeyBytes = new byte[NEXT_KEY_LENGTH];
    System.arraycopy(ciphertext, NEXT_KEY_OFFSET, nextKeyBytes, 0, nextKeyBytes.length);

    return nextKeyBytes;
  }

  public void setCounter(int counter) {
    Conversions.mediumToByteArray(ciphertext, COUNTER_OFFSET, counter);
  }

  public int getCounter() {
    return Conversions.byteArrayToMedium(ciphertext, COUNTER_OFFSET);
  }

  public void setBody(byte[] body) {
    System.arraycopy(body, 0, ciphertext, BODY_OFFSET, body.length);
  }

  public byte[] getBody() {
    byte[] body = new byte[ciphertext.length - HEADER_LENGTH - MessageMac.MAC_LENGTH];
    System.arraycopy(ciphertext, BODY_OFFSET, body, 0, body.length);

    return body;
  }

  public void setMac(byte[] mac) {
    System.arraycopy(mac, 0, ciphertext, ciphertext.length-mac.length, mac.length);
  }

  public byte[] getMac() {
    byte[] mac = new byte[MessageMac.MAC_LENGTH];
    System.arraycopy(ciphertext, ciphertext.length-mac.length, mac, 0, mac.length);

    return mac;
  }

  public byte[] serialize() {
    return ciphertext;
  }

  public void verifyMac(SessionCipher.SessionCipherContext sessionContext)
      throws InvalidMessageException
  {
    try {
      MessageMac.verifyMac(this.ciphertext, 0, this.ciphertext.length - MessageMac.MAC_LENGTH,
                           getMac(), sessionContext.getSessionKey().getMacKey());
    } catch (InvalidMacException e) {
      throw new InvalidMessageException(e);
    }
  }

}
