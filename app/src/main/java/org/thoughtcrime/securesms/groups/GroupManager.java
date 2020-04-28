package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GroupManager {

  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms)
  {
    Set<RecipientId> addresses = getMemberIds(members);

    return V1GroupManager.createGroup(context, addresses, avatar, name, mms);
  }

  @WorkerThread
  public static GroupActionResult updateGroup(@NonNull  Context context,
                                              @NonNull  GroupId groupId,
                                              @Nullable byte[]  avatar,
                                              @Nullable String  name)
      throws InvalidNumberException
  {

    List<Recipient> members = DatabaseFactory.getGroupDatabase(context)
                                             .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

    return V1GroupManager.updateGroup(context, groupId, getMemberIds(members), avatar, name);
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  GroupId        groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name)
      throws InvalidNumberException
  {
    Set<RecipientId> addresses = getMemberIds(members);

    return V1GroupManager.updateGroup(context, groupId, addresses, BitmapUtil.toByteArray(avatar), name);
  }

  private static Set<RecipientId> getMemberIds(Collection<Recipient> recipients) {
    final Set<RecipientId> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getId());
    }

    return results;
  }

  @WorkerThread
  public static boolean leaveGroup(@NonNull Context context, @NonNull GroupId.Push groupId) {
    return V1GroupManager.leaveGroup(context, groupId.requireV1());
  }

  @WorkerThread
  public static void updateGroupTimer(@NonNull Context context, @NonNull GroupId.Push groupId, int expirationTime)
      throws GroupChangeFailedException, GroupInsufficientRightsException
  {
    if (groupId.isV2()) {
      throw new GroupChangeFailedException(new AssertionError("NYI")); // TODO: GV2 allow timer change
    } else {
      V1GroupManager.updateGroupTimer(context, groupId.requireV1(), expirationTime);
    }
  }

  @WorkerThread
  public static void cancelInvites(@NonNull Context context,
                                   @NonNull GroupId.V2 groupId,
                                   @NonNull Collection<UuidCiphertext> uuidCipherTexts)
      throws InvalidGroupStateException, VerificationFailedException, IOException
  {
    throw new AssertionError("NYI"); // TODO: GV2 allow invite cancellation
  }

  @WorkerThread
  public static void applyMembershipAdditionRightsChange(@NonNull Context context,
                                                         @NonNull GroupId.V2 groupId,
                                                         @NonNull GroupAccessControl newRights)
      throws GroupChangeFailedException, GroupInsufficientRightsException
  {
    throw new GroupChangeFailedException(new AssertionError("NYI")); // TODO: GV2 allow membership addition rights change
  }

  @WorkerThread
  public static void applyAttributesRightsChange(@NonNull Context context,
                                                 @NonNull GroupId.V2 groupId,
                                                 @NonNull GroupAccessControl newRights)
      throws GroupChangeFailedException, GroupInsufficientRightsException
  {
    throw new GroupChangeFailedException(new AssertionError("NYI")); // TODO: GV2 allow attributes rights change
  }

  public static class GroupActionResult {
    private final Recipient groupRecipient;
    private final long      threadId;

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
