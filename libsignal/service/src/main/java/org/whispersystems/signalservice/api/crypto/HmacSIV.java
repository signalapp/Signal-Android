package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.util.StringUtil;

import java.util.Arrays;

import static java.util.Arrays.copyOfRange;
import static org.whispersystems.signalservice.api.crypto.CryptoUtil.hmacSha256;
import static org.whispersystems.util.ByteArrayUtil.concat;
import static org.whispersystems.util.ByteArrayUtil.xor;

/**
 * Encrypts or decrypts with a Synthetic IV.
 * <p>
 * Normal Java casing has been ignored to match original specifications.
 */
public final class HmacSIV {

  private static final byte[] AUTH_BYTES = StringUtil.utf8("auth");
  private static final byte[] ENC_BYTES  = StringUtil.utf8("enc");

  /**
   * Encrypts M with K.
   *
   * @param K Key
   * @param M 32-byte Key to encrypt
   * @return (IV, C) 48-bytes: 16-byte Synthetic IV and 32-byte Ciphertext.
   */
  public static byte[] encrypt(byte[] K, byte[] M) {
    if (K.length != 32) throw new AssertionError("K was wrong length");
    if (M.length != 32) throw new AssertionError("M was wrong length");

    byte[] Ka = hmacSha256(K, AUTH_BYTES);
    byte[] Ke = hmacSha256(K, ENC_BYTES);
    byte[] IV = copyOfRange(hmacSha256(Ka, M), 0, 16);
    byte[] Kx = hmacSha256(Ke, IV);
    byte[] C  = xor(Kx, M);
    return concat(IV, C);
  }

  /**
   * Decrypts M from (IV, C) with K.
   *
   * @param K   Key
   * @param IVC Output from {@link #encrypt(byte[], byte[])}
   * @return 32-byte M
   * @throws InvalidCiphertextException if the supplied IVC was not correct.
   */
  public static byte[] decrypt(byte[] K, byte[] IVC) throws InvalidCiphertextException {
    if (K.length   != 32) throw new AssertionError("K was wrong length");
    if (IVC.length != 48) throw new InvalidCiphertextException("IVC was wrong length");

    byte[] IV = copyOfRange(IVC, 0, 16);
    byte[] C  = copyOfRange(IVC, 16, 48);

    byte[] Ka = hmacSha256(K, AUTH_BYTES);
    byte[] Ke = hmacSha256(K, ENC_BYTES);
    byte[] Kx = hmacSha256(Ke, IV);
    byte[] M  = xor(Kx, C);

    byte[] eExpectedIV = copyOfRange(hmacSha256(Ka, M), 0, 16);

    if (Arrays.equals(IV, eExpectedIV)) {
      return M;
    } else {
      throw new InvalidCiphertextException("IV was incorrect");
    }
  }
}
