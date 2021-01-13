package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.contacts.Contact;
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview;
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel;
import org.session.libsession.messaging.threads.recipients.Recipient;

import java.util.Collections;
import java.util.List;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipient recipient, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType,
                                    long expiresIn,
                                    @Nullable QuoteModel quote,
                                    @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews)
  {
    super(recipient, body, attachments, sentTimeMillis, -1, expiresIn, distributionType, quote, contacts, previews, Collections.emptyList(), Collections.emptyList());
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
