/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.state.impl;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InMemorySessionStore implements SessionStore {

  private Map<SignalProtocolAddress, byte[]> sessions = new HashMap<SignalProtocolAddress, byte[]>();

  public InMemorySessionStore() {}

  @Override
  public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
    try {
      if (containsSession(remoteAddress)) {
        return new SessionRecord(sessions.get(remoteAddress));
      } else {
        return new SessionRecord();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public synchronized List<Integer> getSubDeviceSessions(String name) {
    List<Integer> deviceIds = new LinkedList<Integer>();

    for (SignalProtocolAddress key : sessions.keySet()) {
      if (key.getName().equals(name) &&
          key.getDeviceId() != 1)
      {
        deviceIds.add(key.getDeviceId());
      }
    }

    return deviceIds;
  }

  @Override
  public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
    sessions.put(address, record.serialize());
  }

  @Override
  public synchronized boolean containsSession(SignalProtocolAddress address) {
    return sessions.containsKey(address);
  }

  @Override
  public synchronized void deleteSession(SignalProtocolAddress address) {
    sessions.remove(address);
  }

  @Override
  public synchronized void deleteAllSessions(String name) {
    for (SignalProtocolAddress key : sessions.keySet()) {
      if (key.getName().equals(name)) {
        sessions.remove(key);
      }
    }
  }
}
