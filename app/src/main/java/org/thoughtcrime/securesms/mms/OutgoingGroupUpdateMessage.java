package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.GroupV2UpdateMessageUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collections;
import java.util.List;

public final class OutgoingGroupUpdateMessage extends OutgoingSecureMediaMessage {

  private final MessageGroupContext messageGroupContext;

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                    @NonNull MessageGroupContext groupContext,
                                    @NonNull List<Attachment> avatar,
                                    long sentTimeMillis,
                                    long expiresIn,
                                    boolean viewOnce,
                                    @Nullable QuoteModel quote,
                                    @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    super(recipient,
          groupContext.getEncodedGroupContext(),
          avatar,
          sentTimeMillis,
          ThreadTable.DistributionTypes.CONVERSATION,
          expiresIn,
          viewOnce,
          StoryType.NONE,
          null,
          false,
          quote,
          contacts,
          previews,
          mentions,
          null);

    this.messageGroupContext = groupContext;
  }

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                    @NonNull GroupContext group,
                                    @Nullable final Attachment avatar,
                                    long sentTimeMillis,
                                    long expireIn,
                                    boolean viewOnce,
                                    @Nullable QuoteModel quote,
                                    @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    this(recipient, new MessageGroupContext(group), getAttachments(avatar), sentTimeMillis, expireIn, viewOnce, quote, contacts, previews, mentions);
  }

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                    @NonNull DecryptedGroupV2Context group,
                                    long sentTimeMillis)
  {
    this(recipient, new MessageGroupContext(group), Collections.emptyList(), sentTimeMillis, 0, false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isV2Group() {
    return GroupV2UpdateMessageUtil.isGroupV2(messageGroupContext);
  }

  public boolean isJustAGroupLeave() {
    return GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext);
  }

  public @NonNull MessageGroupContext.GroupV1Properties requireGroupV1Properties() {
    return messageGroupContext.requireGroupV1Properties();
  }

  public @NonNull MessageGroupContext.GroupV2Properties requireGroupV2Properties() {
    return messageGroupContext.requireGroupV2Properties();
  }

  @Override
  public boolean isUrgent() {
    return false;
  }

  private static List<Attachment> getAttachments(@Nullable Attachment avatar) {
    return avatar == null ? Collections.emptyList() : Collections.singletonList(avatar);
  }
}
