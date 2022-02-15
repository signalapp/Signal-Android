/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.crypto;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDF;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionEnvelope;
import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage;


public class PrimaryProvisioningCipher {

  public static final String PROVISIONING_MESSAGE = "TextSecure Provisioning Message";

  private final ECPublicKey theirPublicKey;

  public PrimaryProvisioningCipher(ECPublicKey theirPublicKey) {
    this.theirPublicKey = theirPublicKey;
  }

  public byte[] encrypt(ProvisionMessage message) throws InvalidKeyException {
    ECKeyPair ourKeyPair    = Curve.generateKeyPair();
    byte[]    sharedSecret  = Curve.calculateAgreement(theirPublicKey, ourKeyPair.getPrivateKey());
    byte[]    derivedSecret = HKDF.deriveSecrets(sharedSecret, PROVISIONING_MESSAGE.getBytes(), 64);
    byte[][]  parts         = Util.split(derivedSecret, 32, 32);

    byte[] version    = {0x01};
    byte[] ciphertext = getCiphertext(parts[0], message.toByteArray());
    byte[] mac        = getMac(parts[1], Util.join(version, ciphertext));
    byte[] body       = Util.join(version, ciphertext, mac);

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
