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

  private final GroupContext group;

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull String encodedGroupContext,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
      throws IOException
  {
    super(recipient, encodedGroupContext, avatar, sentTimeMillis,
          DistributionTypes.CONVERSATION, expiresIn, quote, contacts, previews);

    this.group = GroupContext.parseFrom(Base64.decode(encodedGroupContext));
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long expireIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    super(recipient, Base64.encodeBytes(group.toByteArray()),
            new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
            System.currentTimeMillis(),
            DistributionTypes.CONVERSATION, expireIn, quote, contacts, previews);

    this.group = group;
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTime,
                                   long expireIn,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    super(recipient, Base64.encodeBytes(group.toByteArray()),
          new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
          sentTime,
          DistributionTypes.CONVERSATION, expireIn, quote, contacts, previews);

    this.group = group;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isGroupUpdate() {
    return group.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isGroupQuit() {
    return group.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

  public GroupContext getGroupContext() {
    return group;
  }
}
