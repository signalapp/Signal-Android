package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GroupManager {

  public static long getPublicChatThreadId(String id, @NonNull  Context context) {
    final String groupId = GroupUtil.getEncodedPublicChatId(id.getBytes());
    return getThreadIdFromGroupId(groupId, context);
  }

  public static long getRSSFeedThreadId(String id, @NonNull  Context context) {
    final String groupId = GroupUtil.getEncodedRSSFeedId(id.getBytes());
    return getThreadIdFromGroupId(groupId, context);
  }

  public static long getThreadIdFromGroupId(String groupId, @NonNull  Context context) {
    final Recipient groupRecipient  = Recipient.from(context, Address.fromSerialized(groupId), false);
    return DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(groupRecipient);
  }

  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms,
                                                       @NonNull  Set<Recipient> admins)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    String id = GroupUtil.getEncodedId(database.allocateGroupId(), mms);
    return createGroup(id, context, members, avatar, name, mms, admins);
  }

  public static @NonNull GroupActionResult createGroup(@NonNull  String         id,
                                                       @NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms,
                                                       @NonNull  Set<Recipient> admins)
  {
    final byte[]        avatarBytes     = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase   = DatabaseFactory.getGroupDatabase(context);
    final String        groupId         = GroupUtil.getEncodedId(id.getBytes(), mms);
    final Recipient     groupRecipient  = Recipient.from(context, Address.fromSerialized(groupId), false);
    final Set<Address>  memberAddresses = getMemberAddresses(members);
    final Set<Address>  adminAddresses  = getMemberAddresses(admins);

    String masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
    String ourNumber = masterHexEncodedPublicKey != null ? masterHexEncodedPublicKey : TextSecurePreferences.getLocalNumber(context);

    memberAddresses.add(Address.fromSerialized(ourNumber));
    groupDatabase.create(groupId, name, new LinkedList<>(memberAddresses), null, null, new LinkedList<>(adminAddresses));

    if (!mms) {
      groupDatabase.updateAvatar(groupId, avatarBytes);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient, true);
      return sendGroupUpdate(context, groupId, memberAddresses, name, avatarBytes, adminAddresses);
    } else {
      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  public static @NonNull GroupActionResult createPublicChatGroup(@NonNull  String         id,
                                                                 @NonNull  Context        context,
                                                                 @Nullable Bitmap         avatar,
                                                                 @Nullable String         name)
  {
    final String groupId = GroupUtil.getEncodedPublicChatId(id.getBytes());
    return createLokiGroup(groupId, context, avatar, name);
  }

  public static @NonNull GroupActionResult createRSSFeedGroup(@NonNull  String         id,
                                                              @NonNull  Context        context,
                                                              @Nullable Bitmap         avatar,
                                                              @Nullable String         name)
  {
    final String groupId = GroupUtil.getEncodedRSSFeedId(id.getBytes());
    return createLokiGroup(groupId, context, avatar, name);
  }

  private static @NonNull GroupActionResult createLokiGroup(@NonNull  String         groupId,
                                                            @NonNull  Context        context,
                                                            @Nullable Bitmap         avatar,
                                                            @Nullable String         name)
  {
    final byte[]        avatarBytes     = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase   = DatabaseFactory.getGroupDatabase(context);
    final Recipient     groupRecipient  = Recipient.from(context, Address.fromSerialized(groupId), false);
    final Set<Address>  memberAddresses = new HashSet<>();

    memberAddresses.add(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)));
    groupDatabase.create(groupId, name, new LinkedList<>(memberAddresses), null, null, new LinkedList<>());

    groupDatabase.updateAvatar(groupId, avatarBytes);

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
    return new GroupActionResult(groupRecipient, threadId);
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  String         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name,
                                              @NonNull  Set<Recipient> admins)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase   = DatabaseFactory.getGroupDatabase(context);
    final Set<Address>  memberAddresses = getMemberAddresses(members);
    final Set<Address>  adminAddresses  = getMemberAddresses(admins);
    final byte[]        avatarBytes     = BitmapUtil.toByteArray(avatar);

    memberAddresses.add(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)));
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));
    groupDatabase.updateAdmins(groupId, new LinkedList<>(adminAddresses));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    if (!GroupUtil.isMmsGroup(groupId)) {
      return sendGroupUpdate(context, groupId, memberAddresses, name, avatarBytes, adminAddresses);
    } else {
      Recipient groupRecipient = Recipient.from(context, Address.fromSerialized(groupId), true);
      long      threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context      context,
                                                   @NonNull  String       groupId,
                                                   @NonNull  Set<Address> members,
                                                   @Nullable String       groupName,
                                                   @Nullable byte[]       avatar,
                                                   @NonNull  Set<Address> admins)
  {
    try {
      Attachment avatarAttachment = null;
      Address    groupAddress     = Address.fromSerialized(groupId);
      Recipient  groupRecipient   = Recipient.from(context, groupAddress, false);

      List<String> numbers = new LinkedList<>();
      for (Address member : members) {
        numbers.add(member.serialize());
      }

      List<String> adminNumbers = new LinkedList<>();
      for (Address admin : admins) {
        adminNumbers.add(admin.serialize());
      }

      GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                             .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupId)))
                                                             .setType(GroupContext.Type.UPDATE)
                                                             .addAllMembers(numbers)
                                                             .addAllAdmins(adminNumbers);
      if (groupName != null) groupContextBuilder.setName(groupName);
      GroupContext groupContext = groupContextBuilder.build();

      if (avatar != null) {
        Uri avatarUri = BlobProvider.getInstance().forData(avatar).createForSingleUseInMemory();
        avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false, false, null, null);
      }

      OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0, null, Collections.emptyList(), Collections.emptyList());
      long                      threadId        = MessageSender.send(context, outgoingMessage, -1, false, null);

      return new GroupActionResult(groupRecipient, threadId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static Set<Address> getMemberAddresses(Collection<Recipient> recipients) {
    final Set<Address> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getAddress());
    }

    return results;
  }

  public static class GroupActionResult {
    private Recipient groupRecipient;
    private long      threadId;

    public GroupActionResult(Recipient groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipient getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
