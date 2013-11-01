package org.whispersystems.textsecure.crypto.kdf;

import android.util.Log;

import org.whispersystems.textsecure.util.Conversions;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

public class NKDF extends KDF {

  @Override
  public DerivedSecrets deriveSecrets(List<BigInteger> sharedSecret,
                                      boolean isLowEnd, byte[] info)
  {
    SecretKeySpec cipherKey = deriveCipherSecret(isLowEnd, sharedSecret);
    SecretKeySpec macKey    = deriveMacSecret(cipherKey);

    return new DerivedSecrets(cipherKey, macKey);
  }

  private SecretKeySpec deriveCipherSecret(boolean isLowEnd, List<BigInteger> sharedSecret) {
    byte[] sharedSecretBytes = concatenateSharedSecrets(sharedSecret);
    byte[] derivedBytes      = deriveBytes(sharedSecretBytes, 16 * 2);
    byte[] cipherSecret      = new byte[16];

    if (isLowEnd)  {
      System.arraycopy(derivedBytes, 16, cipherSecret, 0, 16);
    } else {
      System.arraycopy(derivedBytes, 0, cipherSecret, 0, 16);
    }

    return new SecretKeySpec(cipherSecret, "AES");
  }

  private SecretKeySpec deriveMacSecret(SecretKeySpec key) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] secret    = md.digest(key.getEncoded());

      return new SecretKeySpec(secret, "HmacSHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("SHA-1 Not Supported!",e);
    }
  }

  private byte[] deriveBytes(byte[] seed, int bytesNeeded) {
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      Log.w("NKDF", e);
      throw new IllegalArgumentException("SHA-256 Not Supported!");
    }

    int rounds = bytesNeeded / md.getDigestLength();

    for (int i=1;i<=rounds;i++) {
      byte[] roundBytes = Conversions.intToByteArray(i);
      md.update(roundBytes);
      md.update(seed);
    }

    return md.digest();
  }


}
