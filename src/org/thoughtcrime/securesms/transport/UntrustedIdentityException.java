package org.thoughtcrime.securesms.transport;

import org.whispersystems.textsecure.crypto.IdentityKey;

public class UntrustedIdentityException extends Exception {

  private final IdentityKey identityKey;

  public UntrustedIdentityException(String s, IdentityKey identityKey) {
    super(s);
    this.identityKey = identityKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }
}
