package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public final class GroupExistsException extends NonSuccessfulResponseCodeException {
  public GroupExistsException() {
    super(409);
  }
}
