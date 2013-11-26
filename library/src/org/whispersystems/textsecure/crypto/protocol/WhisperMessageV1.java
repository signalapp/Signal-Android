package org.whispersystems.textsecure.crypto.protocol;

import android.util.Log;

import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.SessionCipherV1;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Util;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class WhisperMessageV1 implements CiphertextMessage{

  private static final int VERSION_LENGTH         = 1;
  private static final int SENDER_KEY_ID_LENGTH   = 3;
  private static final int RECEIVER_KEY_ID_LENGTH = 3;
  private static final int NEXT_KEY_LENGTH        = PublicKey.KEY_SIZE;
  private static final int COUNTER_LENGTH         = 3;
  private static final int HEADER_LENGTH          =  VERSION_LENGTH + SENDER_KEY_ID_LENGTH   +
                                                     RECEIVER_KEY_ID_LENGTH + COUNTER_LENGTH +
                                                     NEXT_KEY_LENGTH;
  private static final int MAC_LENGTH             = 10;


  private static final int VERSION_OFFSET         = 0;
  private static final int SENDER_KEY_ID_OFFSET   = VERSION_OFFSET + VERSION_LENGTH;
  private static final int RECEIVER_KEY_ID_OFFSET = SENDER_KEY_ID_OFFSET + SENDER_KEY_ID_LENGTH;
  private static final int NEXT_KEY_OFFSET        = RECEIVER_KEY_ID_OFFSET + RECEIVER_KEY_ID_LENGTH;
  private static final int COUNTER_OFFSET         = NEXT_KEY_OFFSET + NEXT_KEY_LENGTH;
  private static final int BODY_OFFSET            = COUNTER_OFFSET + COUNTER_LENGTH;

          static final int ENCRYPTED_MESSAGE_OVERHEAD = HEADER_LENGTH + MAC_LENGTH;

  private final byte[] ciphertext;

  public WhisperMessageV1(SessionCipherV1.SessionCipherContext sessionContext,
                          byte[] ciphertextBody)
  {
    this.ciphertext = new byte[HEADER_LENGTH + ciphertextBody.length + MAC_LENGTH];
    setVersion(sessionContext.getMessageVersion(), CURRENT_VERSION);
    setSenderKeyId(sessionContext.getSenderKeyId());
    setReceiverKeyId(sessionContext.getRecipientKeyId());
    setNextKeyBytes(sessionContext.getNextKey().serialize());
    setCounter(sessionContext.getCounter());
    setBody(ciphertextBody);
    setMac(calculateMac(sessionContext.getSessionKey().getMacKey(),
                        ciphertext, 0, ciphertext.length - MAC_LENGTH));
  }

  public WhisperMessageV1(byte[] ciphertext) throws InvalidMessageException {
    this.ciphertext = ciphertext;

    if (ciphertext.length < HEADER_LENGTH) {
      throw new InvalidMessageException("Not long enough for ciphertext header!");
    }

    if (getCurrentVersion() > LEGACY_VERSION) {
      throw new InvalidMessageException("Received non-legacy version: " + getCurrentVersion());
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
    byte[] body = new byte[ciphertext.length - HEADER_LENGTH - MAC_LENGTH];
    System.arraycopy(ciphertext, BODY_OFFSET, body, 0, body.length);

    return body;
  }

  public void setMac(byte[] mac) {
    System.arraycopy(mac, 0, ciphertext, ciphertext.length-mac.length, mac.length);
  }

  public byte[] getMac() {
    byte[] mac = new byte[MAC_LENGTH];
    System.arraycopy(ciphertext, ciphertext.length-mac.length, mac, 0, mac.length);

    return mac;
  }

  @Override
  public byte[] serialize() {
    return ciphertext;
  }

  @Override
  public int getType() {
    return CiphertextMessage.LEGACY_WHISPER_TYPE;
  }

  public void verifyMac(SessionCipherV1.SessionCipherContext sessionContext)
      throws InvalidMessageException
  {
    verifyMac(sessionContext.getSessionKey().getMacKey(),
              this.ciphertext, 0, this.ciphertext.length - MAC_LENGTH, getMac());
  }

  private byte[] calculateMac(SecretKeySpec macKey, byte[] message, int offset, int length) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(macKey);

      mac.update(message, offset, length);
      byte[] macBytes = mac.doFinal();

      return Util.trim(macBytes, MAC_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void verifyMac(SecretKeySpec macKey, byte[] message, int offset, int length,
                         byte[] receivedMac)
      throws InvalidMessageException
  {
    byte[] localMac = calculateMac(macKey, message, offset, length);

    Log.w("WhisperMessageV1", "Local Mac: " + Hex.toString(localMac));
    Log.w("WhisperMessageV1", "Remot Mac: " + Hex.toString(receivedMac));

    if (!Arrays.equals(localMac, receivedMac)) {
      throw new InvalidMessageException("MAC on message does not match calculated MAC.");
    }
  }

}
