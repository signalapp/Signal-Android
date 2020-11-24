package org.whispersystems.signalservice.api.messages.calls;


public class AnswerMessage {

  private final long   id;
  private final String description;

  public AnswerMessage(long id, String description) {
    this.id          = id;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public long getId() {
    return id;
  }
}
