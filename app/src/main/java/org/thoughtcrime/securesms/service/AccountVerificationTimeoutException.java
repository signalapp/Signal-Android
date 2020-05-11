package org.thoughtcrime.securesms.service;

public class AccountVerificationTimeoutException extends Exception {
  public AccountVerificationTimeoutException() {
  }

  public AccountVerificationTimeoutException(String detailMessage) {
    super(detailMessage);
  }

  public AccountVerificationTimeoutException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public AccountVerificationTimeoutException(Throwable throwable) {
    super(throwable);
  }
}
