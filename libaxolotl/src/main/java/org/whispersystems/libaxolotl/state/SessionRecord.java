package org.whispersystems.libaxolotl.state;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libaxolotl.state.StorageProtos.RecordStructure;
import static org.whispersystems.libaxolotl.state.StorageProtos.SessionStructure;

/**
 * A SessionRecord encapsulates the state of an ongoing session.
 *
 * @author Moxie Marlinspike
 */
public class SessionRecord {

  private SessionState       sessionState   = new SessionState();
  private List<SessionState> previousStates = new LinkedList<>();

  public SessionRecord() {}

  public SessionRecord(SessionState sessionState) {
    this.sessionState = sessionState;
  }

  public SessionRecord(byte[] serialized) throws IOException {
    RecordStructure record = RecordStructure.parseFrom(serialized);
    this.sessionState = new SessionState(record.getCurrentSession());

    for (SessionStructure previousStructure : record.getPreviousSessionsList()) {
      previousStates.add(new SessionState(previousStructure));
    }
  }

  public boolean hasSessionState(int version, byte[] aliceBaseKey) {
    if (sessionState.getSessionVersion() == version &&
        Arrays.equals(aliceBaseKey, sessionState.getAliceBaseKey()))
    {
      return true;
    }

    for (SessionState state : previousStates) {
      if (state.getSessionVersion() == version &&
          Arrays.equals(aliceBaseKey, state.getAliceBaseKey()))
      {
        return true;
      }
    }

    return false;
  }

  public SessionState getSessionState() {
    return sessionState;
  }

  /**
   * @return the list of all currently maintained "previous" session states.
   */
  public List<SessionState> getPreviousSessionStates() {
    return previousStates;
  }

  /**
   * Reset the current SessionRecord, clearing all "previous" session states,
   * and resetting the current {@link org.whispersystems.libaxolotl.state.SessionState}
   * to a fresh state.
   */
  public void reset() {
    this.sessionState   = new SessionState();
    this.previousStates = new LinkedList<>();
  }

  /**
   * Move the current {@link SessionState} into the list of "previous" session states,
   * and replace the current {@link org.whispersystems.libaxolotl.state.SessionState}
   * with a fresh reset instance.
   */
  public void archiveCurrentState() {
    this.previousStates.add(sessionState);
    this.sessionState = new SessionState();
  }

  /**
   * @return a serialized version of the current SessionRecord.
   */
  public byte[] serialize() {
    List<SessionStructure> previousStructures = new LinkedList<>();

    for (SessionState previousState : previousStates) {
      previousStructures.add(previousState.getStructure());
    }

    RecordStructure record = RecordStructure.newBuilder()
                                            .setCurrentSession(sessionState.getStructure())
                                            .addAllPreviousSessions(previousStructures)
                                            .build();

    return record.toByteArray();
  }

}
