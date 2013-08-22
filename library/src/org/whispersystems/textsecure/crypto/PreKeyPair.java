package org.whispersystems.textsecure.crypto;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.whispersystems.textsecure.util.Util;

public class PreKeyPair {

  private final MasterCipher           masterCipher;
  private final ECPrivateKeyParameters privateKey;
  private final PreKeyPublic           publicKey;

  public PreKeyPair(MasterSecret masterSecret, AsymmetricCipherKeyPair keyPair) {
    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PreKeyPublic((ECPublicKeyParameters)keyPair.getPublic());
    this.privateKey   = (ECPrivateKeyParameters)keyPair.getPrivate();
  }

  public PreKeyPair(MasterSecret masterSecret, byte[] serialized) throws InvalidKeyException {
    if (serialized.length < KeyUtil.POINT_SIZE + 1)
      throw new InvalidKeyException("Serialized length: " + serialized.length);

    byte[] privateKeyBytes = new byte[serialized.length - KeyUtil.POINT_SIZE];
    System.arraycopy(serialized, KeyUtil.POINT_SIZE, privateKeyBytes, 0, privateKeyBytes.length);

    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PreKeyPublic(serialized, 0);
    this.privateKey   = masterCipher.decryptKey(privateKeyBytes);
  }

  public PreKeyPublic getPublicKey() {
    return publicKey;
  }

  public AsymmetricCipherKeyPair getKeyPair() {
    return new AsymmetricCipherKeyPair(publicKey.getPublicKey(), privateKey);
  }

  public byte[] serialize() {
    byte[] publicKeyBytes  = publicKey.serialize();
    byte[] privateKeyBytes = masterCipher.encryptKey(privateKey);

    return Util.combine(publicKeyBytes, privateKeyBytes);
  }
}
