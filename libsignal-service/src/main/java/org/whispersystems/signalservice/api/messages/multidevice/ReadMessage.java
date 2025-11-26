/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.push.ServiceId;

public class ReadMessage {

  private final ServiceId.ACI sender;
  private final long          timestamp;

  public ReadMessage(ServiceId.ACI sender, long timestamp) {
    this.sender    = sender;
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public ServiceId.ACI getSenderAci() {
    return sender;
  }

}
