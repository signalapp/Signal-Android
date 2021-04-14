package org.session.libsession.messaging.messages.signal;

import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.threads.DistributionTypes;
import org.session.libsession.messaging.threads.recipients.Recipient;

import java.util.Collections;
import java.util.LinkedList;

// TODO this class could be deleted if its usage in MmsDatabase.getOutgoingMessage is replaced by something elsex
public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient, "", new LinkedList<Attachment>(), sentTimeMillis,
          DistributionTypes.CONVERSATION, expiresIn, true, null, Collections.emptyList(),
          Collections.emptyList());
  }

  public static OutgoingExpirationUpdateMessage from(ExpirationTimerUpdate message,
                                          Recipient recipient) {
    return new OutgoingExpirationUpdateMessage(recipient, message.getSentTimestamp(), message.getDuration() * 1000);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
