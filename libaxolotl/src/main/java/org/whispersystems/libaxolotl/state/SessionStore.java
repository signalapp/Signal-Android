package org.whispersystems.libaxolotl.state;

import java.util.List;

public interface SessionStore {

  public SessionRecord get(long recipientId, int deviceId);
  public List<Integer> getSubDeviceSessions(long recipientId);
  public void put(long recipientId, int deviceId, SessionRecord record);
  public boolean contains(long recipientId, int deviceId);
  public void delete(long recipientId, int deviceId);
  public void deleteAll(long recipientId);

}
