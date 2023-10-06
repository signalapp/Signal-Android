package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.push.ServiceId;

public class ViewOnceOpenMessage {

  private final ServiceId sender;
  private final long      timestamp;

  public ViewOnceOpenMessage(ServiceId sender, long timestamp) {
    this.sender    = sender;
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public ServiceId getSender() {
    return sender;
  }

}
