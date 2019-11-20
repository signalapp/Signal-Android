package org.whispersystems.signalservice.internal.contacts.crypto;

public class RemoteAttestation {

  private final byte[]                requestId;
  private final RemoteAttestationKeys keys;

  public RemoteAttestation(byte[] requestId, RemoteAttestationKeys keys) {
    this.requestId = requestId;
    this.keys      = keys;
  }

  public byte[] getRequestId() {
    return requestId;
  }

  public RemoteAttestationKeys getKeys() {
    return keys;
  }
}
