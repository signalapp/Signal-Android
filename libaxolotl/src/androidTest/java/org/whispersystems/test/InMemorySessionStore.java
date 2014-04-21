package org.whispersystems.test;

import org.whispersystems.libaxolotl.SessionState;
import org.whispersystems.libaxolotl.SessionStore;

import java.util.LinkedList;
import java.util.List;

public class InMemorySessionStore implements SessionStore {

  private SessionState currentSessionState;
  private List<SessionState> previousSessionStates;

  private SessionState       checkedOutSessionState;
  private List<SessionState> checkedOutPreviousSessionStates;

  public InMemorySessionStore(SessionState sessionState) {
    this.currentSessionState             = sessionState;
    this.previousSessionStates           = new LinkedList<>();
    this.checkedOutPreviousSessionStates = new LinkedList<>();
  }

  @Override
  public SessionState getSessionState() {
    checkedOutSessionState = new InMemorySessionState(currentSessionState);
    return checkedOutSessionState;
  }

  @Override
  public List<SessionState> getPreviousSessionStates() {
    checkedOutPreviousSessionStates = new LinkedList<>();
    for (SessionState state : previousSessionStates) {
      checkedOutPreviousSessionStates.add(new InMemorySessionState(state));
    }

    return checkedOutPreviousSessionStates;
  }

  @Override
  public void save() {
    this.currentSessionState   = this.checkedOutSessionState;
    this.previousSessionStates = this.checkedOutPreviousSessionStates;
  }
}
