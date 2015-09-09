package org.thoughtcrime.redphone.crypto.zrtp.retained;

public class RetainedSecretsDerivatives {
  private final byte[] rs1ID;
  private final byte[] rs2ID;

  public RetainedSecretsDerivatives(byte[] rs1ID, byte[] rs2ID) {
    this.rs1ID = rs1ID;
    this.rs2ID = rs2ID;
  }

  public byte[] getRetainedSecretOneDerivative() {
    return rs1ID;
  }

  public byte[] getRetainedSecretTwoDerivative() {
    return rs2ID;
  }
}
