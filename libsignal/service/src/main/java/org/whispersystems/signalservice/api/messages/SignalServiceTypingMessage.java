package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;

public class SignalServiceTypingMessage {

  public enum Action {
    UNKNOWN, STARTED, STOPPED
  }

  private final Action           action;
  private final long             timestamp;
  private final Optional<byte[]> groupId;

  public SignalServiceTypingMessage(Action action, long timestamp, Optional<byte[]> groupId) {
    this.action    = action;
    this.timestamp = timestamp;
    this.groupId   = groupId;
  }

  public Action getAction() {
    return action;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }

  public boolean isTypingStarted() {
    return action == Action.STARTED;
  }

  public boolean isTypingStopped() {
    return action == Action.STOPPED;
  }
}
