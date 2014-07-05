package org.whispersystems.test;

import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.Pair;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InMemorySessionStore implements SessionStore {

  private Map<Pair<Long, Integer>, byte[]> sessions = new HashMap<>();

  public InMemorySessionStore() {}

  @Override
  public synchronized SessionRecord loadSession(long recipientId, int deviceId) {
    try {
      if (containsSession(recipientId, deviceId)) {
        return new SessionRecord(sessions.get(new Pair<>(recipientId, deviceId)));
      } else {
        return new SessionRecord();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public synchronized List<Integer> getSubDeviceSessions(long recipientId) {
    List<Integer> deviceIds = new LinkedList<>();

    for (Pair<Long, Integer> key : sessions.keySet()) {
      if (key.first() == recipientId) {
        deviceIds.add(key.second());
      }
    }

    return deviceIds;
  }

  @Override
  public synchronized void storeSession(long recipientId, int deviceId, SessionRecord record) {
    sessions.put(new Pair<>(recipientId, deviceId), record.serialize());
  }

  @Override
  public synchronized boolean containsSession(long recipientId, int deviceId) {
    return sessions.containsKey(new Pair<>(recipientId, deviceId));
  }

  @Override
  public synchronized void deleteSession(long recipientId, int deviceId) {
    sessions.remove(new Pair<>(recipientId, deviceId));
  }

  @Override
  public synchronized void deleteAllSessions(long recipientId) {
    for (Pair<Long, Integer> key : sessions.keySet()) {
      if (key.first() == recipientId) {
        sessions.remove(key);
      }
    }
  }
}
