package org.whispersystems.textsecure.api.push.exceptions;

public class NetworkFailureException extends Exception {

  private final String e164number;

  public NetworkFailureException(String e164number, Exception nested) {
    super(nested);
    this.e164number = e164number;
  }

  public String getE164number() {
    return e164number;
  }
}
