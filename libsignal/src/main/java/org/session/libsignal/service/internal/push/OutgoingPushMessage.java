/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.internal.push;


import com.fasterxml.jackson.annotation.JsonProperty;

public class OutgoingPushMessage {

  @JsonProperty
  public int    type;
  @JsonProperty
  public String content;

  public OutgoingPushMessage(int type, String content)
  {
    this.type                      = type;
    this.content                   = content;
  }
}
