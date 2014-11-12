package org.whispersystems.textsecure.api.push.exceptions;

public class NotFoundException extends NonSuccessfulResponseCodeException {
  public NotFoundException(String s) {
    super(s);
  }
}
