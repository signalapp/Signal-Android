package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.groups.v2.GroupLinkPassword;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GroupManager {

  private static final String TAG = Log.tag(GroupManager.class);

  @WorkerThread
  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable byte[]         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    boolean          shouldAttemptToCreateV2 = !mms && !SignalStore.internalValues().gv2DoNotCreateGv2Groups();
    Set<RecipientId> memberIds               = getMemberIds(members);

    if (shouldAttemptToCreateV2) {
      try {
        try (GroupManagerV2.GroupCreator groupCreator = new GroupManagerV2(context).create()) {
          return groupCreator.createGroup(memberIds, name, avatar);
        }
      } catch (MembershipNotSuitableForV2Exception e) {
        Log.w(TAG, "Attempted to make a GV2, but membership was not suitable, falling back to GV1", e);

        return GroupManagerV1.createGroup(context, memberIds, avatar, name, false);
      }
    } else {
      return GroupManagerV1.createGroup(context, memberIds, avatar, name, mms);
    }
  }

  @WorkerThread
  public static GroupActionResult updateGroupDetails(@NonNull  Context context,
                                                     @NonNull  GroupId groupId,
                                                     @Nullable byte[]  avatar,
                                                               boolean avatarChanged,
                                                     @NonNull  String  name,
                                                               boolean nameChanged)
    throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
        return edit.updateGroupTitleAndAvatar(nameChanged ? name : null, avatar, avatarChanged);
      }
    } else if (groupId.isV1()) {
      List<Recipient> members = DatabaseFactory.getGroupDatabase(context)
                                               .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

      Set<RecipientId> recipientIds = getMemberIds(new HashSet<>(members));

      return GroupManagerV1.updateGroup(context, groupId.requireV1(), recipientIds, avatar, name, 0);
    } else {
      return GroupManagerV1.updateGroup(context, groupId.requireMms(), avatar, name);
    }
  }

  @WorkerThread
  public static void migrateGroupToServer(@NonNull Context context,
                                          @NonNull GroupId.V1 groupIdV1,
                                          @NonNull Collection<Recipient> members)
      throws IOException, GroupChangeFailedException, MembershipNotSuitableForV2Exception, GroupAlreadyExistsException
  {
    new GroupManagerV2(context).migrateGroupOnToServer(groupIdV1, members);
  }

  private static Set<RecipientId> getMemberIds(Collection<Recipient> recipients) {
    Set<RecipientId> results = new HashSet<>(recipients.size());

    for (Recipient recipient : recipients) {
      results.add(recipient.getId());
    }

    return results;
  }

  @WorkerThread
  public static void leaveGroup(@NonNull Context context, @NonNull GroupId.Push groupId)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
        edit.leaveGroup();
        Log.i(TAG, "Left group " + groupId);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, "Unexpected prevention from leaving " + groupId + " due to rights", e);
        throw new GroupChangeFailedException(e);
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "Already left group " + groupId, e);
      }
    } else {
      if (!GroupManagerV1.leaveGroup(context, groupId.requireV1())) {
        Log.w(TAG, "GV1 group leave failed" + groupId);
        throw new GroupChangeFailedException();
      }
    }
  }

  @WorkerThread
  public static void leaveGroupFromBlockOrMessageRequest(@NonNull Context context, @NonNull GroupId.Push groupId)
      throws IOException, GroupChangeBusyException, GroupChangeFailedException
  {
    if (groupId.isV2()) {
      leaveGroup(context, groupId.requireV2());
    } else {
      if (!GroupManagerV1.silentLeaveGroup(context, groupId.requireV1())) {
        throw new GroupChangeFailedException();
      }
    }
  }

  @WorkerThread
  public static void addMemberAdminsAndLeaveGroup(@NonNull Context context, @NonNull GroupId.V2 groupId, @NonNull Collection<RecipientId> newAdmins)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException, GroupInsufficientRightsException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.addMemberAdminsAndLeaveGroup(newAdmins);
      Log.i(TAG, "Left group " + groupId);
    }
  }

  @WorkerThread
  public static void ejectFromGroup(@NonNull Context context, @NonNull GroupId.V2 groupId, @NonNull Recipient recipient)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupInsufficientRightsException, GroupNotAMemberException, IOException
  {
    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.ejectMember(recipient.getId());
      Log.i(TAG, "Member removed from group " + groupId);
    }
  }

  /**
   * @throws GroupNotAMemberException When Self is not a member of the group.
   *                                  The exception to this is when Self is a requesting member and
   *                                  there is a supplied signedGroupChange. This allows for
   *                                  processing deny messages.
   */
  @WorkerThread
  public static void updateGroupFromServer(@NonNull Context context,
                                           @NonNull GroupMasterKey groupMasterKey,
                                           int revision,
                                           long timestamp,
                                           @Nullable byte[] signedGroupChange)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      updater.updateLocalToServerRevision(revision, timestamp, signedGroupChange);
    }
  }

  @WorkerThread
  public static V2GroupServerStatus v2GroupStatus(@NonNull Context context,
                                                  @NonNull GroupMasterKey groupMasterKey)
      throws IOException
  {
    try {
      new GroupManagerV2(context).groupServerQuery(groupMasterKey);
      return V2GroupServerStatus.FULL_OR_PENDING_MEMBER;
    } catch (GroupNotAMemberException e) {
      return V2GroupServerStatus.NOT_A_MEMBER;
    } catch (GroupDoesNotExistException e) {
      return V2GroupServerStatus.DOES_NOT_EXIST;
    }
  }

  /**
   * Tries to gets the exact version of the group at the time you joined.
   * <p>
   * If it fails to get the exact version, it will give the latest.
   */
  @WorkerThread
  public static DecryptedGroup addedGroupVersion(@NonNull Context context,
                                                 @NonNull GroupMasterKey groupMasterKey)
    throws IOException, GroupDoesNotExistException, GroupNotAMemberException
  {
    return new GroupManagerV2(context).addedGroupVersion(groupMasterKey);
  }

  @WorkerThread
  public static void setMemberAdmin(@NonNull Context context,
                                    @NonNull GroupId.V2 groupId,
                                    @NonNull RecipientId recipientId,
                                    boolean admin)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupInsufficientRightsException, GroupNotAMemberException, IOException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.setMemberAdmin(recipientId, admin);
    }
  }

  @WorkerThread
  public static void updateSelfProfileKeyInGroup(@NonNull Context context, @NonNull GroupId.V2 groupId)
      throws IOException, GroupChangeBusyException, GroupInsufficientRightsException, GroupNotAMemberException, GroupChangeFailedException
  {
    if (!DatabaseFactory.getGroupDatabase(context).groupExists(groupId)) {
      Log.i(TAG, "Group is not available locally " + groupId);
      return;
    }

    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateSelfProfileKeyInGroup();
    }
  }

  @WorkerThread
  public static void acceptInvite(@NonNull Context context, @NonNull GroupId.V2 groupId)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.acceptInvite();
      DatabaseFactory.getGroupDatabase(context)
                     .setActive(groupId, true);
    }
  }

  @WorkerThread
  public static void updateGroupTimer(@NonNull Context context, @NonNull GroupId.Push groupId, int expirationTime)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
        editor.updateGroupTimer(expirationTime);
      }
    } else {
      GroupManagerV1.updateGroupTimer(context, groupId.requireV1(), expirationTime);
    }
  }

  @WorkerThread
  public static void revokeInvites(@NonNull Context context,
                                   @NonNull GroupId.V2 groupId,
                                   @NonNull Collection<UuidCiphertext> uuidCipherTexts)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.revokeInvites(uuidCipherTexts);
    }
  }

  @WorkerThread
  public static void applyMembershipAdditionRightsChange(@NonNull Context context,
                                                         @NonNull GroupId.V2 groupId,
                                                         @NonNull GroupAccessControl newRights)
       throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateMembershipRights(newRights);
    }
  }

  @WorkerThread
  public static void applyAttributesRightsChange(@NonNull Context context,
                                                 @NonNull GroupId.V2 groupId,
                                                 @NonNull GroupAccessControl newRights)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateAttributesRights(newRights);
    }
  }

  @WorkerThread
  public static void cycleGroupLinkPassword(@NonNull Context context,
                                            @NonNull GroupId.V2 groupId)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.cycleGroupLinkPassword();
    }
  }

  @WorkerThread
  public static GroupInviteLinkUrl setGroupLinkEnabledState(@NonNull Context context,
                                                            @NonNull GroupId.V2 groupId,
                                                            @NonNull GroupLinkState state)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      return editor.setJoinByGroupLinkState(state);
    }
  }

  @WorkerThread
  public static void approveRequests(@NonNull Context context,
                                     @NonNull GroupId.V2 groupId,
                                     @NonNull Collection<RecipientId> recipientIds)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.approveRequests(recipientIds);
    }
  }

  @WorkerThread
  public static void denyRequests(@NonNull Context context,
                                  @NonNull GroupId.V2 groupId,
                                  @NonNull Collection<RecipientId> recipientIds)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.denyRequests(recipientIds);
    }
  }

  @WorkerThread
  public static @NonNull GroupActionResult addMembers(@NonNull Context context,
                                                      @NonNull GroupId.Push groupId,
                                                      @NonNull Collection<RecipientId> newMembers)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException, MembershipNotSuitableForV2Exception
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
        return editor.addMembers(newMembers);
      }
    } else {
      GroupDatabase.GroupRecord groupRecord  = DatabaseFactory.getGroupDatabase(context).requireGroup(groupId);
      List<RecipientId>         members      = groupRecord.getMembers();
      byte[]                    avatar       = groupRecord.hasAvatar() ? AvatarHelper.getAvatarBytes(context, groupRecord.getRecipientId()) : null;
      Set<RecipientId>          recipientIds = new HashSet<>(members);
      int                       originalSize = recipientIds.size();

      recipientIds.addAll(newMembers);
      return GroupManagerV1.updateGroup(context, groupId, recipientIds, avatar, groupRecord.getTitle(), recipientIds.size() - originalSize);
    }
  }

  /**
   * Use to get a group's details direct from server bypassing the database.
   * <p>
   * Useful when you don't yet have the group in the database locally.
   */
  @WorkerThread
  public static @NonNull DecryptedGroupJoinInfo getGroupJoinInfoFromServer(@NonNull Context context,
                                                                           @NonNull GroupMasterKey groupMasterKey,
                                                                           @Nullable GroupLinkPassword groupLinkPassword)
      throws IOException, VerificationFailedException, GroupLinkNotActiveException
  {
    return new GroupManagerV2(context).getGroupJoinInfoFromServer(groupMasterKey, groupLinkPassword);
  }

  @WorkerThread
  public static GroupActionResult joinGroup(@NonNull Context context,
                                            @NonNull GroupMasterKey groupMasterKey,
                                            @NonNull GroupLinkPassword groupLinkPassword,
                                            @NonNull DecryptedGroupJoinInfo decryptedGroupJoinInfo,
                                            @Nullable byte[] avatar)
      throws IOException, GroupChangeBusyException, GroupChangeFailedException, MembershipNotSuitableForV2Exception, GroupLinkNotActiveException
  {
    try (GroupManagerV2.GroupJoiner join = new GroupManagerV2(context).join(groupMasterKey, groupLinkPassword)) {
      return join.joinGroup(decryptedGroupJoinInfo, avatar);
    }
  }

  @WorkerThread
  public static void cancelJoinRequest(@NonNull Context context,
                                       @NonNull GroupId.V2 groupId)
      throws GroupChangeFailedException, IOException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupJoiner editor = new GroupManagerV2(context).cancelRequest(groupId.requireV2())) {
      editor.cancelJoinRequest();
    }
  }

  public static void sendNoopUpdate(@NonNull Context context, @NonNull GroupMasterKey groupMasterKey, @NonNull DecryptedGroup currentState) {
    new GroupManagerV2(context).sendNoopGroupUpdate(groupMasterKey, currentState);
  }

  @WorkerThread
  public static @NonNull GroupExternalCredential getGroupExternalCredential(@NonNull Context context,
                                                                            @NonNull GroupId.V2 groupId)
      throws IOException, VerificationFailedException
  {
    return new GroupManagerV2(context).getGroupExternalCredential(groupId);
  }

  @WorkerThread
  public static @NonNull Map<UUID, UuidCiphertext> getUuidCipherTexts(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    return new GroupManagerV2(context).getUuidCipherTexts(groupId);
  }

  public static class GroupActionResult {
    private final Recipient         groupRecipient;
    private final long              threadId;
    private final int               addedMemberCount;
    private final List<RecipientId> invitedMembers;

    public GroupActionResult(@NonNull Recipient groupRecipient,
                             long threadId,
                             int addedMemberCount,
                             @NonNull List<RecipientId> invitedMembers)
    {
      this.groupRecipient   = groupRecipient;
      this.threadId         = threadId;
      this.addedMemberCount = addedMemberCount;
      this.invitedMembers   = invitedMembers;
    }

    public @NonNull Recipient getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }

    public int getAddedMemberCount() {
      return addedMemberCount;
    }

    public @NonNull List<RecipientId> getInvitedMembers() {
      return invitedMembers;
    }
  }

  public enum GroupLinkState {
    DISABLED,
    ENABLED,
    ENABLED_WITH_APPROVAL
  }

  public enum V2GroupServerStatus {
    /** The group does not exist. The expected pre-migration state for V1 groups. */
    DOES_NOT_EXIST,
    /** Group exists but self is not in the group. */
    NOT_A_MEMBER,
    /** Self is a full or pending member of the group. */
    FULL_OR_PENDING_MEMBER
  }
}
