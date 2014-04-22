package org.whispersystems.test;

import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;

import java.util.LinkedList;
import java.util.List;

public class InMemorySessionRecord implements SessionRecord {

  private SessionState       currentSessionState;
  private List<SessionState> previousSessionStates;

  public InMemorySessionRecord() {
    currentSessionState   = new InMemorySessionState();
    previousSessionStates = new LinkedList<>();
  }

  public InMemorySessionRecord(SessionRecord copy) {
    currentSessionState   = new InMemorySessionState(copy.getSessionState());
    previousSessionStates = new LinkedList<>();

    for (SessionState previousState : copy.getPreviousSessionStates()) {
      previousSessionStates.add(new InMemorySessionState(previousState));
    }
  }

  @Override
  public SessionState getSessionState() {
    return currentSessionState;
  }

  @Override
  public List<SessionState> getPreviousSessionStates() {
    return previousSessionStates;
  }

  @Override
  public void reset() {
    this.currentSessionState   = new InMemorySessionState();
    this.previousSessionStates = new LinkedList<>();
  }

  @Override
  public void archiveCurrentState() {
    this.previousSessionStates.add(currentSessionState);
    this.currentSessionState = new InMemorySessionState();
  }

  @Override
  public byte[] serialize() {
    throw new AssertionError();
  }
}
