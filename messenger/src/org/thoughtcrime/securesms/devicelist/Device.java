package org.thoughtcrime.securesms.devicelist;

public class Device {

  private final String id;
  private final String shortId;
  private final String name;

  public Device(String id, String shortId, String name) {
    this.id = id;
    this.shortId = shortId;
    this.name = name;
  }

  public String getId() {
    return id;
  }
  public String getShortId() { return shortId; }
  public String getName() { return name; }
}
