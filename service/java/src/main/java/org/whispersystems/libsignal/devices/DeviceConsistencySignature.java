package org.whispersystems.libsignal.devices;

public class DeviceConsistencySignature {

  private final byte[] signature;
  private final byte[] vrfOutput;

  public DeviceConsistencySignature(byte[] signature, byte[] vrfOutput) {
    this.signature = signature;
    this.vrfOutput = vrfOutput;
  }

  public byte[] getVrfOutput() {
    return vrfOutput;
  }

  public byte[] getSignature() {
    return signature;
  }

}
