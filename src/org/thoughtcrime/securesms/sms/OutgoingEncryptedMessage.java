package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.recipients.Recipient;

public class OutgoingEncryptedMessage extends OutgoingTextMessage {

  public OutgoingEncryptedMessage(Recipient recipient, String body, long expiresIn) {
    super(recipient, body, expiresIn, -1);
  }

  private OutgoingEncryptedMessage(OutgoingEncryptedMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingEncryptedMessage(this, body);
  }
}
