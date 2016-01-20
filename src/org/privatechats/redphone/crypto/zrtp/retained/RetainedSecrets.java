package org.privatechats.redphone.crypto.zrtp.retained;

public class RetainedSecrets {

  private final byte[] rs1;
  private final byte[] rs2;

  public RetainedSecrets(byte[] rs1, byte[] rs2) {
    this.rs1 = rs1;
    this.rs2 = rs2;
  }

  public byte[] getRetainedSecretOne() {
    return rs1;
  }

  public byte[] getRetainedSecretTwo() {
    return rs2;
  }
}
