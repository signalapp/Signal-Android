package org.thoughtcrime.securesms.devicelist;

public class Device {

  private final long   id;
  private final String name;
  private final long   created;
  private final long   lastSeen;

  public Device(long id, String name, long created, long lastSeen) {
    this.id       = id;
    this.name     = name;
    this.created  = created;
    this.lastSeen = lastSeen;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public long getCreated() {
    return created;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
