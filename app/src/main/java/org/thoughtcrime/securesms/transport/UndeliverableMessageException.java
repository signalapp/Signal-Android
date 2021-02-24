package org.thoughtcrime.securesms.transport;

public class UndeliverableMessageException extends Exception {

  public UndeliverableMessageException(String detailMessage) {
    super(detailMessage);
  }

  public UndeliverableMessageException(Throwable throwable) {
    super(throwable);
  }
}
