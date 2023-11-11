package org.whispersystems.signalservice.api.push.exceptions;

public class DeprecatedVersionException extends NonSuccessfulResponseCodeException {
  public DeprecatedVersionException() {
    super(499);
  }
}
