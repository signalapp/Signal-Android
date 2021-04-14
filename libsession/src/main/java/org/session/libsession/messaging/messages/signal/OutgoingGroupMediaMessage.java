package org.session.libsession.messaging.messages.signal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.threads.DistributionTypes;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.sharecontacts.Contact;
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview;
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsignal.utilities.Base64;

import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class OutgoingGroupMediaMessage extends OutgoingSecureMediaMessage {

  private final String groupID;

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull String body,
                                   @Nullable String groupId,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
      throws IOException
  {
    super(recipient, body, avatar, sentTimeMillis,
          DistributionTypes.CONVERSATION, expiresIn, false, quote, contacts, previews);

    this.groupID = groupId;
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull String body,
                                   @Nullable String groupId,
                                   @Nullable final Attachment avatar,
                                   long sentTime,
                                   long expireIn,
                                   boolean expirationUpdate,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    super(recipient, body,
          new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
          sentTime,
          DistributionTypes.CONVERSATION, expireIn, expirationUpdate, quote, contacts, previews);

    this.groupID = groupId;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public String getGroupId() {
    return groupID;
  }
}
