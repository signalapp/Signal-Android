package org.thoughtcrime.securesms.mms;

public class ApnUnavailableException extends Exception {

  public ApnUnavailableException() {
  }

  public ApnUnavailableException(String detailMessage) {
    super(detailMessage);
  }

  public ApnUnavailableException(Throwable throwable) {
    super(throwable);
  }

  public ApnUnavailableException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }
}
