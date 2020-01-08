package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupManager.GroupActionResult;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

final class V1GroupManager {

  static @NonNull GroupActionResult createGroup(@NonNull Context          context,
                                                @NonNull Set<RecipientId> memberIds,
                                                @Nullable Bitmap          avatar,
                                                @Nullable String          name,
                                                          boolean         mms)
  {
    final byte[]        avatarBytes      = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
    final String        groupId          = GroupUtil.getEncodedId(groupDatabase.allocateGroupId(), mms);
    final RecipientId   groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    final Recipient     groupRecipient   = Recipient.resolved(groupRecipientId);

    memberIds.add(Recipient.self().getId());
    groupDatabase.create(groupId, name, new LinkedList<>(memberIds), null, null);

    if (!mms) {
      groupDatabase.updateAvatar(groupId, avatarBytes);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);
      return sendGroupUpdate(context, groupId, memberIds, name, avatarBytes);
    } else {
      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  static GroupActionResult updateGroup(@NonNull  Context          context,
                                       @NonNull  String           groupId,
                                       @NonNull  Set<RecipientId> memberAddresses,
                                       @Nullable Bitmap           avatar,
                                       @Nullable String           name)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
    final byte[]        avatarBytes   = BitmapUtil.toByteArray(avatar);

    memberAddresses.add(Recipient.self().getId());
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    if (!GroupUtil.isMmsGroup(groupId)) {
      return sendGroupUpdate(context, groupId, memberAddresses, name, avatarBytes);
    } else {
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);
      long        threadId         = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context          context,
                                                   @NonNull  String           groupId,
                                                   @NonNull  Set<RecipientId> members,
                                                   @Nullable String           groupName,
                                                   @Nullable byte[]           avatar)
  {
    try {
      Attachment  avatarAttachment = null;
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

      List<GroupContext.Member> uuidMembers = new LinkedList<>();
      List<String>              e164Members = new LinkedList<>();

      for (RecipientId member : members) {
        Recipient recipient = Recipient.resolved(member);
        uuidMembers.add(GroupMessageProcessor.createMember(RecipientUtil.toSignalServiceAddress(context, recipient)));
      }

      GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                             .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupId)))
                                                             .setType(GroupContext.Type.UPDATE)
                                                             .addAllMembersE164(e164Members)
                                                             .addAllMembers(uuidMembers);
      if (groupName != null) groupContextBuilder.setName(groupName);
      GroupContext groupContext = groupContextBuilder.build();

      if (avatar != null) {
        Uri avatarUri = BlobProvider.getInstance().forData(avatar).createForSingleUseInMemory();
        avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false, false, null, null, null, null);
      }

      OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0, false, null, Collections.emptyList(), Collections.emptyList());
      long                      threadId        = MessageSender.send(context, outgoingMessage, -1, false, null);

      return new GroupActionResult(groupRecipient, threadId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
