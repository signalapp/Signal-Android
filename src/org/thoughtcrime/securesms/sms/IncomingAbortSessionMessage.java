package org.thoughtcrime.securesms.sms;

public class IncomingAbortSessionMessage extends IncomingTextMessage {

  IncomingAbortSessionMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);
  }

  @Override
  public IncomingTextMessage withMessageBody(String messageBody) {
    return new IncomingAbortSessionMessage(this, messageBody);
  }

  @Override
  public boolean isAbortSession() {
    return true;
  }
}
