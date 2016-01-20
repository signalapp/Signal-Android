package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.List;

import ws.com.google.android.mms.pdu.PduBody;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipients recipients, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType)
  {
    super(recipients, body, attachments, sentTimeMillis, distributionType);
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
