package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public final class GroupNotFoundException extends NonSuccessfulResponseCodeException {
  public GroupNotFoundException() {
    super(404);
  }
}
