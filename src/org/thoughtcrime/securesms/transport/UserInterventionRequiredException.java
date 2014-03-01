package org.thoughtcrime.securesms.transport;

public class UserInterventionRequiredException extends Exception {
  public UserInterventionRequiredException(String detailMessage) {
    super(detailMessage);
  }
}
