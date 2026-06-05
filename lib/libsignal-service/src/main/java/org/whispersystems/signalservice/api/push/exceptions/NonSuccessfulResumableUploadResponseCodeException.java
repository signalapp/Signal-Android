package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

public class NonSuccessfulResumableUploadResponseCodeException extends NonSuccessfulResponseCodeException {
  public NonSuccessfulResumableUploadResponseCodeException(int code, String s) {
    super(code, s);
  }
}
