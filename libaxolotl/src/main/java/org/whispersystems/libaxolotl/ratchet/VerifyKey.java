package org.whispersystems.libaxolotl.ratchet;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;

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

  public byte[] generateVerification(IdentityKey           aliceIdentity,
                                     IdentityKey           bobIdentity,
                                     ECPublicKey           aliceBaseKey,
                                     ECPublicKey           bobSignedPreKey,
                                     Optional<ECPublicKey> bobOneTimePreKey)
  {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      mac.update(VERIFICATION_INFO);
      mac.update(aliceIdentity.getPublicKey().serialize());
      mac.update(bobIdentity.getPublicKey().serialize());
      mac.update(aliceBaseKey.serialize());
      mac.update(bobSignedPreKey.serialize());

      if (bobOneTimePreKey.isPresent()) {
        mac.update(bobOneTimePreKey.get().serialize());
      }

      return ByteUtil.trim(mac.doFinal(), 8);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}
