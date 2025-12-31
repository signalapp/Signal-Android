package org.whispersystems.signalservice.api.crypto;


import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.zkgroup.internal.ByteArray;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class UnidentifiedAccess {

  private final byte[]            unidentifiedAccessKey;
  private final SenderCertificate unidentifiedCertificate;
  private final boolean           isUnrestrictedForStory;

  /**
   * @param isUnrestrictedForStory When sending to a story, we always want to use sealed sender. Receivers will accept it for story messages. However, there are
   *                               some situations where we need to know if this access key will be correct for non-story purposes. Set this flag to true if
   *                               the access key is a synthetic one that would only be valid for story messages.
   */
  public UnidentifiedAccess(byte[] unidentifiedAccessKey, byte[] unidentifiedCertificate, boolean isUnrestrictedForStory)
      throws InvalidCertificateException
  {
    this.unidentifiedAccessKey   = unidentifiedAccessKey;
    this.unidentifiedCertificate = new SenderCertificate(unidentifiedCertificate);
    this.isUnrestrictedForStory  = isUnrestrictedForStory;
  }

  public byte[] getUnidentifiedAccessKey() {
    return unidentifiedAccessKey;
  }

  public SenderCertificate getUnidentifiedCertificate() {
    return unidentifiedCertificate;
  }

  public boolean isUnrestrictedForStory() {
    return isUnrestrictedForStory;
  }

  public static byte[] deriveAccessKeyFrom(ProfileKey profileKey) {
    try {
      byte[] nonce = createEmptyByteArray(12);
      byte[] input = createEmptyByteArray(16);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(profileKey.serialize(), "AES"), new GCMParameterSpec(128, nonce));

      byte[] ciphertext = cipher.doFinal(input);

      return ByteUtil.trim(ciphertext, 16);
    } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] createEmptyByteArray(int length) {
    return new byte[length];
  }
}
