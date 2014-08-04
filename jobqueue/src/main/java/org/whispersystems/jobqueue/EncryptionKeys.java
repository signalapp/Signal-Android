package org.whispersystems.jobqueue;

public class EncryptionKeys {

  private transient final byte[] encoded;

  public EncryptionKeys(byte[] encoded) {
    this.encoded = encoded;
  }

  public byte[] getEncoded() {
    return encoded;
  }
}
