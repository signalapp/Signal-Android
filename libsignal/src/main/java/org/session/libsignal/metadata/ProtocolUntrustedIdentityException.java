package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.UntrustedIdentityException;

public class ProtocolUntrustedIdentityException extends ProtocolException {
  public ProtocolUntrustedIdentityException(UntrustedIdentityException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
