package org.session.libsession.messaging.messages.signal;

import static org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;

public class IncomingGroupMessage extends IncomingTextMessage {

  private final String groupID;

  public IncomingGroupMessage(IncomingTextMessage base, String groupID, String body) {
    super(base, body);
    this.groupID = groupID;
  }

    @Override
  public boolean isGroup() {
    return true;
  }

}
