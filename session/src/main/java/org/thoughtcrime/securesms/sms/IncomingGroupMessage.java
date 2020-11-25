package org.thoughtcrime.securesms.sms;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class IncomingGroupMessage extends IncomingTextMessage {

  private final GroupContext groupContext;

  public IncomingGroupMessage(IncomingTextMessage base, GroupContext groupContext, String body) {
    super(base, body);
    this.groupContext = groupContext;
  }

  @Override
  public IncomingGroupMessage withMessageBody(String body) {
    return new IncomingGroupMessage(this, groupContext, body);
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isUpdate() {
    return groupContext.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isQuit() {
    return groupContext.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

}
