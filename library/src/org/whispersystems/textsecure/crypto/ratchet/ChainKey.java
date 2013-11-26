package org.whispersystems.textsecure.crypto.ratchet;

import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ChainKey {

  private static final byte[] MESSAGE_KEY_SEED = {0x01};
  private static final byte[] CHAIN_KEY_SEED   = {0x02};

  private final byte[] key;
  private final int    index;

  public ChainKey(byte[] key, int index) {
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
    return new ChainKey(nextKey, index + 1);
  }

  public MessageKeys getMessageKeys() {
    HKDF           kdf              = new HKDF();
    byte[]         inputKeyMaterial = getBaseMaterial(MESSAGE_KEY_SEED);
    DerivedSecrets keyMaterial      = kdf.deriveSecrets(inputKeyMaterial, "WhisperMessageKeys".getBytes());

    return new MessageKeys(keyMaterial.getCipherKey(), keyMaterial.getMacKey(), index);
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
