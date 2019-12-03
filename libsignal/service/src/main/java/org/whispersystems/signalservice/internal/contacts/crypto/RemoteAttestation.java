package org.whispersystems.signalservice.internal.contacts.crypto;

import java.util.List;

public class RemoteAttestation {

  private final byte[]                requestId;
  private final RemoteAttestationKeys keys;
  private final List<String>          cookies;

  public RemoteAttestation(byte[] requestId, RemoteAttestationKeys keys, List<String> cookies) {
    this.requestId = requestId;
    this.keys      = keys;
    this.cookies   = cookies;
  }

  public byte[] getRequestId() {
    return requestId;
  }

  public RemoteAttestationKeys getKeys() {
    return keys;
  }

  public List<String> getCookies() {
    return cookies;
  }
}
