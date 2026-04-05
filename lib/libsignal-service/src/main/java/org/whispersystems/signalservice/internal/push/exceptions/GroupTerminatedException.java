package org.whispersystems.signalservice.internal.push.exceptions;

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupTerminatedException extends NonSuccessfulResponseCodeException {
  public GroupTerminatedException() {
    super(423);
  }
}
