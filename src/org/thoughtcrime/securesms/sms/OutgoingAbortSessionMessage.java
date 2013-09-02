package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.recipients.Recipient;

public class OutgoingAbortSessionMessage extends OutgoingTextMessage {

  public OutgoingAbortSessionMessage(Recipient recipient, String message) {
    super(recipient, message);
  }

  private OutgoingAbortSessionMessage(OutgoingAbortSessionMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isAbortMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingAbortSessionMessage(this, body);
  }
}
