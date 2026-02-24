/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.internal.push.SyncMessage.Request;

public class RequestMessage {

  private final Request request;

  public static RequestMessage forType(Request.Type type) {
    return new RequestMessage(new Request.Builder().type(type).build());
  }

  public RequestMessage(Request request) {
    this.request = request;
  }

  public Request getRequest() {
    return request;
  }

  public boolean isContactsRequest() {
    return request.type == Request.Type.CONTACTS;
  }

  public boolean isBlockedListRequest() {
    return request.type == Request.Type.BLOCKED;
  }

  public boolean isConfigurationRequest() {
    return request.type == Request.Type.CONFIGURATION;
  }

  public boolean isKeysRequest() {
    return request.type == Request.Type.KEYS;
  }

  public boolean isUrgent() {
    return isContactsRequest() || isKeysRequest();
  }
}
