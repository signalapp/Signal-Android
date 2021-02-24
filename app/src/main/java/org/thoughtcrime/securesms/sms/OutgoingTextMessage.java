package org.thoughtcrime.securesms.sms;

import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.session.libsession.messaging.threads.recipients.Recipient;

public class OutgoingTextMessage {

  private final Recipient recipient;
  private final String    message;
  private final int       subscriptionId;
  private final long      expiresIn;

  public OutgoingTextMessage(Recipient recipient, String message, long expiresIn, int subscriptionId) {
    this.recipient      = recipient;
    this.message        = message;
    this.expiresIn      = expiresIn;
    this.subscriptionId = subscriptionId;
  }

  public static OutgoingTextMessage from(VisibleMessage message, Recipient recipient) {
    return new OutgoingTextMessage(recipient, message.getText(), recipient.getExpireMessages() * 1000, -1);
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getMessageBody() {
    return message;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public static OutgoingTextMessage from(SmsMessageRecord record) {
    if (record.isSecure()) {
      return new OutgoingEncryptedMessage(record.getRecipient(), record.getBody(), record.getExpiresIn());
    } else {
      return new OutgoingTextMessage(record.getRecipient(), record.getBody(), record.getExpiresIn(), record.getSubscriptionId());
    }
  }
}
