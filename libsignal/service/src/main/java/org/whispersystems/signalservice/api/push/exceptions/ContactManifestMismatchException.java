package org.whispersystems.signalservice.api.push.exceptions;

public class ContactManifestMismatchException extends ConflictException {

  private final byte[] responseBody;

  public ContactManifestMismatchException(byte[] responseBody) {
    this.responseBody = responseBody;
  }

  public byte[] getResponseBody() {
    return responseBody;
  }
}
