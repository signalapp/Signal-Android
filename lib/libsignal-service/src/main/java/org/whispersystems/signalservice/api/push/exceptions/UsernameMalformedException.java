package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class UsernameMalformedException extends NonSuccessfulResponseCodeException {
  public UsernameMalformedException() {
    super(400);
  }
}
