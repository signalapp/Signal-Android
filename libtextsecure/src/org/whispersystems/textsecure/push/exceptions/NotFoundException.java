package org.whispersystems.textsecure.push.exceptions;

import org.whispersystems.textsecure.push.exceptions.NonSuccessfulResponseCodeException;

public class NotFoundException extends NonSuccessfulResponseCodeException {
  public NotFoundException(String s) {
    super(s);
  }
}
