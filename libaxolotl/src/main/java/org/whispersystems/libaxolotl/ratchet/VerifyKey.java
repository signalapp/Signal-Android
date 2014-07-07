package org.whispersystems.libaxolotl.ratchet;

import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.ByteUtil;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class VerifyKey {

  private static final byte[] VERIFICATION_INFO = "TextSecure Verification Tag".getBytes();

  private final byte[] key;

  public VerifyKey(byte[] key) {
    this.key = key;
  }

  public byte[] getKey() {
    return key;
  }

  public byte[] generateVerification(ECPublicKey aliceBaseKey, ECPublicKey alicePreKey, ECPublicKey aliceIdentityKey,
                                     ECPublicKey bobBaseKey,   ECPublicKey bobPreKey,   ECPublicKey bobIdentityKey)
  {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      mac.update(VERIFICATION_INFO);
      mac.update(aliceBaseKey.serialize());
      mac.update(alicePreKey.serialize());
      mac.update(aliceIdentityKey.serialize());
      mac.update(bobBaseKey.serialize());

      if (bobPreKey != null) {
        mac.update(bobPreKey.serialize());
      }

      mac.update(bobIdentityKey.serialize());

      return ByteUtil.trim(mac.doFinal(), 8);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}
