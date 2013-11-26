package org.whispersystems.textsecure.crypto.ratchet;

import android.util.Pair;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RootKey {

  private final byte[] key;

  public RootKey(byte[] key) {
    this.key = key;
  }

  public byte[] getKeyBytes() {
    return key;
  }

  public Pair<RootKey, ChainKey> createChain(ECPublicKey theirEphemeral, ECKeyPair ourEphemeral)
      throws InvalidKeyException
  {
    HKDF           kdf          = new HKDF();
    byte[]         sharedSecret = Curve.calculateAgreement(theirEphemeral, ourEphemeral.getPrivateKey());
    DerivedSecrets keys         = kdf.deriveSecrets(sharedSecret, key, "WhisperRatchet".getBytes());
    RootKey        newRootKey   = new RootKey(keys.getCipherKey().getEncoded());
    ChainKey       newChainKey  = new ChainKey(keys.getMacKey().getEncoded(), 0);

    return new Pair<RootKey, ChainKey>(newRootKey, newChainKey);
  }
}
