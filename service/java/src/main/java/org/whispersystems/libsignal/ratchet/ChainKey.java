/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.ratchet;


import org.whispersystems.libsignal.kdf.DerivedMessageSecrets;
import org.whispersystems.libsignal.kdf.HKDF;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ChainKey {

  private static final byte[] MESSAGE_KEY_SEED = {0x01};
  private static final byte[] CHAIN_KEY_SEED   = {0x02};

  private final HKDF   kdf;
  private final byte[] key;
  private final int    index;

  public ChainKey(HKDF kdf, byte[] key, int index) {
    this.kdf   = kdf;
    this.key   = key;
    this.index = index;
  }

  public byte[] getKey() {
    return key;
  }

  public int getIndex() {
    return index;
  }

  public ChainKey getNextChainKey() {
    byte[] nextKey = getBaseMaterial(CHAIN_KEY_SEED);
    return new ChainKey(kdf, nextKey, index + 1);
  }

  public MessageKeys getMessageKeys() {
    byte[]                inputKeyMaterial = getBaseMaterial(MESSAGE_KEY_SEED);
    byte[]                keyMaterialBytes = kdf.deriveSecrets(inputKeyMaterial, "WhisperMessageKeys".getBytes(), DerivedMessageSecrets.SIZE);
    DerivedMessageSecrets keyMaterial      = new DerivedMessageSecrets(keyMaterialBytes);

    return new MessageKeys(keyMaterial.getCipherKey(), keyMaterial.getMacKey(), keyMaterial.getIv(), index);
  }

  private byte[] getBaseMaterial(byte[] seed) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      return mac.doFinal(seed);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}
