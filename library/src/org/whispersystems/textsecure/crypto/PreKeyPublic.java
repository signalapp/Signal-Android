package org.whispersystems.textsecure.crypto;

import org.spongycastle.crypto.params.ECPublicKeyParameters;

public class PreKeyPublic {

  private final ECPublicKeyParameters publicKey;

  public PreKeyPublic(ECPublicKeyParameters publicKey) {
    this.publicKey = publicKey;
  }

  public PreKeyPublic(byte[] serialized, int offset) throws InvalidKeyException {
    this.publicKey = KeyUtil.decodePoint(serialized, offset);
  }

  public byte[] serialize() {
    return KeyUtil.encodePoint(publicKey.getQ());
  }

}
