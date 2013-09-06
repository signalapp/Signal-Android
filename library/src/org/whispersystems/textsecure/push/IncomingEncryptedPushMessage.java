package org.whispersystems.textsecure.push;

import android.util.Log;

import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal;
import org.whispersystems.textsecure.util.Hex;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class IncomingEncryptedPushMessage {

  private static final int SUPPORTED_VERSION =  1;
  private static final int CIPHER_KEY_SIZE   = 32;
  private static final int MAC_KEY_SIZE      = 20;
  private static final int MAC_SIZE          = 10;

  private static final int VERSION_OFFSET    =  0;
  private static final int VERSION_LENGTH    =  1;
  private static final int IV_OFFSET         = VERSION_OFFSET + VERSION_LENGTH;
  private static final int IV_LENGTH         = 16;
  private static final int CIPHERTEXT_OFFSET = IV_OFFSET + IV_LENGTH;

  private final IncomingPushMessage incomingPushMessage;

  public IncomingEncryptedPushMessage(String message, String signalingKey)
      throws IOException, InvalidVersionException
  {
    byte[] ciphertext = Base64.decode(message);

    if (ciphertext.length < VERSION_LENGTH || ciphertext[VERSION_OFFSET] != SUPPORTED_VERSION)
      throw new InvalidVersionException("Unsupported version!");

    SecretKeySpec cipherKey  = getCipherKey(signalingKey);
    SecretKeySpec macKey     = getMacKey(signalingKey);

    verifyMac(ciphertext, macKey);

    byte[]                    plaintext = getPlaintext(ciphertext, cipherKey);
    IncomingPushMessageSignal signal    = IncomingPushMessageSignal.parseFrom(plaintext);

    this.incomingPushMessage = new IncomingPushMessage(signal);
  }

  public IncomingPushMessage getIncomingPushMessage() {
    return incomingPushMessage;
  }

  private byte[] getPlaintext(byte[] ciphertext, SecretKeySpec cipherKey) throws IOException {
    try {
      byte[] ivBytes = new byte[IV_LENGTH];
      System.arraycopy(ciphertext, IV_OFFSET, ivBytes, 0, ivBytes.length);
      IvParameterSpec iv = new IvParameterSpec(ivBytes);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv);

      return cipher.doFinal(ciphertext, CIPHERTEXT_OFFSET,
                            ciphertext.length - VERSION_LENGTH - IV_LENGTH - MAC_SIZE);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      Log.w("IncomingEncryptedPushMessage", e);
      throw new IOException("Bad padding?");
    }
  }

  private void verifyMac(byte[] ciphertext, SecretKeySpec macKey) throws IOException {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      if (ciphertext.length < MAC_SIZE + 1)
        throw new IOException("Invalid MAC!");

      mac.update(ciphertext, 0, ciphertext.length - MAC_SIZE);

      byte[] ourMacFull  = mac.doFinal();
      byte[] ourMacBytes = new byte[MAC_SIZE];
      System.arraycopy(ourMacFull, 0, ourMacBytes, 0, ourMacBytes.length);

      byte[] theirMacBytes = new byte[MAC_SIZE];
      System.arraycopy(ciphertext, ciphertext.length-MAC_SIZE, theirMacBytes, 0, theirMacBytes.length);

      Log.w("IncomingEncryptedPushMessage", "Our MAC: " + Hex.toString(ourMacBytes));
      Log.w("IncomingEncryptedPushMessage", "Thr MAC: " + Hex.toString(theirMacBytes));

      if (!Arrays.equals(ourMacBytes, theirMacBytes)) {
        throw new IOException("Invalid MAC compare!");
      }
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }


  private SecretKeySpec getCipherKey(String signalingKey) throws IOException {
    byte[] signalingKeyBytes = Base64.decode(signalingKey);
    byte[] cipherKey         = new byte[CIPHER_KEY_SIZE];
    System.arraycopy(signalingKeyBytes, 0, cipherKey, 0, cipherKey.length);

    return new SecretKeySpec(cipherKey, "AES");
  }


  private SecretKeySpec getMacKey(String signalingKey) throws IOException {
    byte[] signalingKeyBytes = Base64.decode(signalingKey);
    byte[] macKey            = new byte[MAC_KEY_SIZE];
    System.arraycopy(signalingKeyBytes, CIPHER_KEY_SIZE, macKey, 0, macKey.length);

    return new SecretKeySpec(macKey, "HmacSHA256");
  }

}
