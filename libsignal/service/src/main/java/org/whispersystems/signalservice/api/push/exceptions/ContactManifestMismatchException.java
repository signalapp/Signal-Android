package org.whispersystems.signalservice.api.push.exceptions;

public class ContactManifestMismatchException extends NonSuccessfulResponseCodeException {

  private final byte[] responseBody;

  public ContactManifestMismatchException(byte[] responseBody) {
    this.responseBody = responseBody;
  }

  public byte[] getResponseBody() {
    return responseBody;
  }
}
