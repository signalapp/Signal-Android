package org.whispersystems.textsecure.push.exceptions;

import org.whispersystems.textsecure.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.push.UnregisteredUserException;

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
