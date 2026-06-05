package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class DeprecatedVersionException extends NonSuccessfulResponseCodeException {
  public DeprecatedVersionException() {
    super(499);
  }
}
