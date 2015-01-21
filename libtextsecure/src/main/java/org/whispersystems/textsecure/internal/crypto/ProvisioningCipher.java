package org.whispersystems.textsecure.internal.crypto;

import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.kdf.HKDFv3;
import org.whispersystems.textsecure.internal.util.Util;

import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import static org.whispersystems.textsecure.internal.push.ProvisioningProtos.ProvisionEnvelope;
import static org.whispersystems.textsecure.internal.push.ProvisioningProtos.ProvisionMessage;


public class ProvisioningCipher {

  private static final String TAG = ProvisioningCipher.class.getSimpleName();

  private final ECPublicKey theirPublicKey;

  public ProvisioningCipher(ECPublicKey theirPublicKey) {
    this.theirPublicKey = theirPublicKey;
  }

  public byte[] encrypt(ProvisionMessage message) throws InvalidKeyException {
    ECKeyPair ourKeyPair    = Curve.generateKeyPair();
    byte[]    sharedSecret  = Curve.calculateAgreement(theirPublicKey, ourKeyPair.getPrivateKey());
    byte[]    derivedSecret = new HKDFv3().deriveSecrets(sharedSecret, "TextSecure Provisioning Message".getBytes(), 64);
    byte[][]  parts         = Util.split(derivedSecret, 32, 32);

    byte[] version    = {0x01};
    byte[] ciphertext = getCiphertext(parts[0], message.toByteArray());
    byte[] mac        = getMac(parts[1], Util.join(version, ciphertext));
    byte[] body =  Util.join(version, ciphertext, mac);

    return ProvisionEnvelope.newBuilder()
                            .setPublicKey(ByteString.copyFrom(ourKeyPair.getPublicKey().serialize()))
                            .setBody(ByteString.copyFrom(body))
                            .build()
                            .toByteArray();
  }

  private byte[] getCiphertext(byte[] key, byte[] message) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

      return Util.join(cipher.getIV(), cipher.doFinal(message));
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getMac(byte[] key, byte[] message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

}
