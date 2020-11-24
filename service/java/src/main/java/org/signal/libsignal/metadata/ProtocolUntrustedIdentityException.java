package org.signal.libsignal.metadata;


import org.whispersystems.libsignal.UntrustedIdentityException;

public class ProtocolUntrustedIdentityException extends ProtocolException {
  public ProtocolUntrustedIdentityException(UntrustedIdentityException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
