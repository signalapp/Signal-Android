package org.whispersystems.signalservice.api.messages.calls;


public class BusyMessage {

  private final long id;

  public BusyMessage(long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }
}
