package org.whispersystems.libaxolotl.state;

import java.util.List;

/**
 * A SessionRecord encapsulates the state of an ongoing session.
 * <p>
 * It contains the current {@link org.whispersystems.libaxolotl.state.SessionState},
 * in addition to previous {@link SessionState}s for the same recipient, which need
 * to be maintained in some situations.
 *
 * @author Moxie Marlinspike
 */
public interface SessionRecord {

  /**
   * @return the current {@link org.whispersystems.libaxolotl.state.SessionState}
   */
  public SessionState       getSessionState();

  /**
   * @return the list of all currently maintained "previous" session states.
   */
  public List<SessionState> getPreviousSessionStates();

  /**
   * Reset the current SessionRecord, clearing all "previous" session states,
   * and resetting the current {@link org.whispersystems.libaxolotl.state.SessionState}
   * to a fresh state.
   */
  public void               reset();

  /**
   * Move the current {@link SessionState} into the list of "previous" session states,
   * and replace the current {@link org.whispersystems.libaxolotl.state.SessionState}
   * with a fresh reset instance.
   */
  public void               archiveCurrentState();

  /**
   * @return a serialized version of the current SessionRecord.
   */
  public byte[]             serialize();

}
