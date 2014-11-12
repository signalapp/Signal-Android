package org.whispersystems.textsecure.api.push.exceptions;

import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;

import java.util.List;

public class EncapsulatedExceptions extends Throwable {

  private final List<UntrustedIdentityException> untrustedIdentityExceptions;
  private final List<UnregisteredUserException>  unregisteredUserExceptions;

  public EncapsulatedExceptions(List<UntrustedIdentityException> untrustedIdentities,
                                List<UnregisteredUserException> unregisteredUsers)
  {
    this.untrustedIdentityExceptions = untrustedIdentities;
    this.unregisteredUserExceptions  = unregisteredUsers;
  }

  public List<UntrustedIdentityException> getUntrustedIdentityExceptions() {
    return untrustedIdentityExceptions;
  }

  public List<UnregisteredUserException> getUnregisteredUserExceptions() {
    return unregisteredUserExceptions;
  }
}
