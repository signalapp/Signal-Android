package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collections;
import java.util.List;

public final class OutgoingGroupMediaMessage extends OutgoingSecureMediaMessage {

  private final MessageGroupContext messageGroupContext;

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull MessageGroupContext groupContext,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    super(recipient, groupContext.getEncodedGroupContext(), avatar, sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, viewOnce, quote, contacts, previews);

    this.messageGroupContext = groupContext;
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    this(recipient, new MessageGroupContext(group), getAttachments(avatar), sentTimeMillis, expireIn, viewOnce, quote, contacts, previews);
  }

  public OutgoingGroupMediaMessage(@NonNull Recipient recipient,
                                   @NonNull DecryptedGroupV2Context group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                   @NonNull List<LinkPreview> previews)
  {
    this(recipient, new MessageGroupContext(group), getAttachments(avatar), sentTimeMillis, expireIn, viewOnce, quote, contacts, previews);
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isV2Group() {
    return messageGroupContext.isV2Group();
  }

  public @NonNull MessageGroupContext.GroupV1Properties requireGroupV1Properties() {
    return messageGroupContext.requireGroupV1Properties();
  }

  public @NonNull MessageGroupContext.GroupV2Properties requireGroupV2Properties() {
    return messageGroupContext.requireGroupV2Properties();
  }

  private static List<Attachment> getAttachments(@Nullable Attachment avatar) {
    return avatar == null ? Collections.emptyList() : Collections.singletonList(avatar);
  }
}
