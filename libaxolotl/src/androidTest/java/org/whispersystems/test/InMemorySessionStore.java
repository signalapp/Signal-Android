package org.whispersystems.test;

import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.Pair;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InMemorySessionStore implements SessionStore {

  private Map<Pair<Long, Integer>, SessionRecord> sessions = new HashMap<>();

  public InMemorySessionStore() {}

  @Override
  public synchronized SessionRecord load(long recipientId, int deviceId) {
    if (contains(recipientId, deviceId)) {
      return new InMemorySessionRecord(sessions.get(new Pair<>(recipientId, deviceId)));
    } else {
      return new InMemorySessionRecord();
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
  public synchronized void store(long recipientId, int deviceId, SessionRecord record) {
    sessions.put(new Pair<>(recipientId, deviceId), record);
  }

  @Override
  public synchronized boolean contains(long recipientId, int deviceId) {
    return sessions.containsKey(new Pair<>(recipientId, deviceId));
  }

  @Override
  public synchronized void delete(long recipientId, int deviceId) {
    sessions.remove(new Pair<>(recipientId, deviceId));
  }

  @Override
  public synchronized void deleteAll(long recipientId) {
    for (Pair<Long, Integer> key : sessions.keySet()) {
      if (key.first() == recipientId) {
        sessions.remove(key);
      }
    }
  }
}
