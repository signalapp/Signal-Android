package org.thoughtcrime.securesms.gcm;

public class GcmRegistrationTimeoutException extends Exception {
  public GcmRegistrationTimeoutException() {
  }

  public GcmRegistrationTimeoutException(String detailMessage) {
    super(detailMessage);
  }

  public GcmRegistrationTimeoutException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public GcmRegistrationTimeoutException(Throwable throwable) {
    super(throwable);
  }
}
