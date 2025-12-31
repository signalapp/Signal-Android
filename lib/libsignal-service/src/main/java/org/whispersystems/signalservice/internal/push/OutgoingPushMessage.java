/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;


import com.fasterxml.jackson.annotation.JsonProperty;

public class OutgoingPushMessage {

  @JsonProperty
  public int    type;
  @JsonProperty
  public int    destinationDeviceId;
  @JsonProperty
  public int    destinationRegistrationId;
  @JsonProperty
  public String content;

  public OutgoingPushMessage() {}

  public OutgoingPushMessage(int type,
                             int destinationDeviceId,
                             int destinationRegistrationId,
                             String content)
  {
    this.type                      = type;
    this.destinationDeviceId       = destinationDeviceId;
    this.destinationRegistrationId = destinationRegistrationId;
    this.content                   = content;
  }

  public int getDestinationDeviceId() {
    return destinationDeviceId;
  }
}
