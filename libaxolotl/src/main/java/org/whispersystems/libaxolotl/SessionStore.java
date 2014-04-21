package org.whispersystems.libaxolotl;

import java.util.List;

public interface SessionStore {

  public SessionState getSessionState();
  public List<SessionState> getPreviousSessionStates();
  public void save();


}
