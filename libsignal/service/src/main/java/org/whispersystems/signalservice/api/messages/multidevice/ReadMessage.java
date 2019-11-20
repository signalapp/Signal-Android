/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ReadMessage {

  private final SignalServiceAddress sender;
  private final long                 timestamp;

  public ReadMessage(SignalServiceAddress sender, long timestamp) {
    this.sender    = sender;
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public SignalServiceAddress getSender() {
    return sender;
  }

}
