package org.whispersystems.signalservice.api.kbs;

import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.util.StringUtil;

import static org.whispersystems.signalservice.api.crypto.CryptoUtil.hmacSha256;

public final class MasterKey {

  private final byte[] masterKey;

  public MasterKey(byte[] masterKey) {
    if (masterKey.length != 32) throw new AssertionError();

    this.masterKey = masterKey;
  }

  public String deriveRegistrationLock() {
    return Hex.toStringCondensed(derive("Registration Lock"));
  }

  public byte[] deriveStorageServiceKey() {
    return derive("Storage Service Encryption");
  }

  private byte[] derive(String keyName) {
    return hmacSha256(masterKey, StringUtil.utf8(keyName));
  }

  public byte[] serialize() {
    return masterKey.clone();
  }
}
