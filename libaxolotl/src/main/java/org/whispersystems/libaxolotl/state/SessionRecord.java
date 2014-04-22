package org.whispersystems.libaxolotl.state;

import java.util.List;

public interface SessionRecord {

  public SessionState       getSessionState();
  public List<SessionState> getPreviousSessionStates();
  public void               reset();
  public void               archiveCurrentState();
  public byte[]             serialize();

}
