package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.DistributionTypes;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsession.utilities.TextSecurePreferences;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

public class GroupManager {

  public static long getOpenGroupThreadID(String id, @NonNull  Context context) {
    final String groupID = GroupUtil.getEncodedOpenGroupID(id.getBytes());
    return getThreadIDFromGroupID(groupID, context);
  }

  public static long getThreadIDFromGroupID(String groupID, @NonNull  Context context) {
    final Recipient groupRecipient = Recipient.from(context, Address.fromSerialized(groupID), false);
    return DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(groupRecipient);
  }

  public static @NonNull GroupActionResult createOpenGroup(@NonNull  String  id,
                                                           @NonNull  Context context,
                                                           @Nullable Bitmap  avatar,
                                                           @Nullable String  name)
  {
    final String groupID = GroupUtil.getEncodedOpenGroupID(id.getBytes());
    return createLokiGroup(groupID, context, avatar, name);
  }

  private static @NonNull GroupActionResult createLokiGroup(@NonNull  String  groupId,
                                                            @NonNull  Context context,
                                                            @Nullable Bitmap  avatar,
                                                            @Nullable String  name)
  {
    final byte[]        avatarBytes     = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase   = DatabaseFactory.getGroupDatabase(context);
    final Recipient     groupRecipient  = Recipient.from(context, Address.fromSerialized(groupId), false);
    final Set<Address>  memberAddresses = new HashSet<>();

    memberAddresses.add(Address.fromSerialized(Objects.requireNonNull(TextSecurePreferences.getLocalNumber(context))));
    groupDatabase.create(groupId, name, new LinkedList<>(memberAddresses), null, null, new LinkedList<>(), System.currentTimeMillis());

    groupDatabase.updateProfilePicture(groupId, avatarBytes);

    long threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(
            groupRecipient, DistributionTypes.CONVERSATION);
    return new GroupActionResult(groupRecipient, threadID);
  }

  public static boolean deleteGroup(@NonNull String  groupId,
                                    @NonNull Context context)
  {
    final GroupDatabase  groupDatabase  = DatabaseFactory.getGroupDatabase(context);
    final ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    final Recipient      groupRecipient = Recipient.from(context, Address.fromSerialized(groupId), false);

    if (!groupDatabase.getGroup(groupId).isPresent()) {
      return false;
    }

    long threadId = threadDatabase.getThreadIdIfExistsFor(groupRecipient);
    if (threadId != -1L) {
      threadDatabase.deleteConversation(threadId);
    }

    return groupDatabase.delete(groupId);
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
