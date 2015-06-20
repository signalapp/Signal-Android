package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.textsecure.internal.push.TextSecureProtos.SyncMessage.Request;

public class RequestMessage {

  private final Request request;

  public RequestMessage(Request request) {
    this.request = request;
  }

  public boolean isContactsRequest() {
    return request.getType() == Request.Type.CONTACTS;
  }
}
