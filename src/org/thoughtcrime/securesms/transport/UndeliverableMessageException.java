package org.thoughtcrime.securesms.transport;

public class UndeliverableMessageException extends Throwable {
  public UndeliverableMessageException() {
  }

  public UndeliverableMessageException(String detailMessage) {
    super(detailMessage);
  }

  public UndeliverableMessageException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public UndeliverableMessageException(Throwable throwable) {
    super(throwable);
  }
}
