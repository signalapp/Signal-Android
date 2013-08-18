package org.whispersystems.textsecure.crypto;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.whispersystems.textsecure.util.Util;

public class PreKeyPair {

  private final MasterCipher masterCipher;
  private final ECPrivateKeyParameters privateKey;
  private final ECPublicKeyParameters publicKey;

  public PreKeyPair(MasterSecret masterSecret, AsymmetricCipherKeyPair keyPair) {
    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = (ECPublicKeyParameters)keyPair.getPublic();
    this.privateKey   = (ECPrivateKeyParameters)keyPair.getPrivate();
  }

  public PreKeyPair(MasterSecret masterSecret, byte[] serialized) throws InvalidKeyException {
    if (serialized.length < KeyUtil.POINT_SIZE + 1)
      throw new InvalidKeyException("Serialized length: " + serialized.length);

    byte[] privateKeyBytes = new byte[serialized.length - KeyUtil.POINT_SIZE];
    System.arraycopy(serialized, KeyUtil.POINT_SIZE, privateKeyBytes, 0, privateKeyBytes.length);

    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = KeyUtil.decodePoint(serialized, 0);
    this.privateKey   = masterCipher.decryptKey(privateKeyBytes);
  }

  public ECPublicKeyParameters getPublicKey() {
    return publicKey;
  }

  public byte[] serialize() {
    byte[] publicKeyBytes  = KeyUtil.encodePoint(publicKey.getQ());
    byte[] privateKeyBytes = masterCipher.encryptKey(privateKey);

    return Util.combine(publicKeyBytes, privateKeyBytes);
  }
}
