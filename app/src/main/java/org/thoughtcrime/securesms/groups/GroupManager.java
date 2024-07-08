package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.groups.v2.GroupLinkPassword;
import org.thoughtcrime.securesms.groups.v2.processing.GroupUpdateResult;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GroupManager {

  private static final String TAG = Log.tag(GroupManager.class);

  @WorkerThread
  public static @NonNull GroupActionResult createGroup(@NonNull Context context,
                                                       @NonNull Set<RecipientId> members,
                                                       @Nullable byte[] avatar,
                                                       @Nullable String name,
                                                       int disappearingMessagesTimer)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    try (GroupManagerV2.GroupCreator groupCreator = new GroupManagerV2(context).create()) {
      return groupCreator.createGroup(members, name, avatar, disappearingMessagesTimer);
    } catch (MembershipNotSuitableForV2Exception e) {
      Log.w(TAG, "Attempted to make a GV2, but membership was not suitable", e);
      throw new GroupChangeFailedException(e);
    }
  }

  @WorkerThread
  public static void updateGroupDetails(@NonNull Context context,
                                        @NonNull GroupId groupId,
                                        @Nullable byte[] avatar,
                                        boolean avatarChanged,
                                        @NonNull String name,
                                        boolean nameChanged,
                                        @NonNull String description,
                                        boolean descriptionChanged)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (!groupId.isV2()) {
      throw new GroupChangeFailedException("Not gv2");
    }

    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.updateGroupTitleDescriptionAndAvatar(nameChanged ? name : null,
                                                descriptionChanged ? description : null,
                                                avatar,
                                                avatarChanged);
    }
  }

  @WorkerThread
  public static void leaveGroup(@NonNull Context context, @NonNull GroupId.Push groupId, boolean sendToMembers)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    if (!groupId.isV2()) {
      throw new GroupChangeFailedException("Not gv2");
    }

    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.leaveGroup(sendToMembers);
      Log.i(TAG, "Left group " + groupId);
    } catch (GroupInsufficientRightsException e) {
      Log.w(TAG, "Unexpected prevention from leaving " + groupId + " due to rights", e);
      throw new GroupChangeFailedException(e);
    } catch (GroupNotAMemberException e) {
      Log.w(TAG, "Already left group " + groupId, e);
    }

    SignalDatabase.recipients().getByGroupId(groupId).ifPresent(id -> SignalDatabase.messages().deleteScheduledMessages(id));
  }

  @WorkerThread
  public static void leaveGroupFromBlockOrMessageRequest(@NonNull Context context, @NonNull GroupId.Push groupId)
      throws IOException, GroupChangeBusyException, GroupChangeFailedException
  {
    if (!groupId.isV2()) {
      throw new GroupChangeFailedException("Not gv2");
    }

    leaveGroup(context, groupId.requireV2(), true);
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
  public static void ejectAndBanFromGroup(@NonNull Context context, @NonNull GroupId.V2 groupId, @NonNull Recipient recipient)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupInsufficientRightsException, GroupNotAMemberException, IOException
  {
    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.ejectMember(recipient.requireAci(), false, true, true);
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
                                           long timestamp)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      updater.updateLocalToServerRevision(revision, timestamp);
    }
  }

  @WorkerThread
  public static GroupUpdateResult updateGroupFromServer(@NonNull Context context,
                                                        @NonNull GroupMasterKey groupMasterKey,
                                                        @NonNull Optional<GroupRecord> groupRecord,
                                                        @Nullable GroupSecretParams groupSecretParams,
                                                        int revision,
                                                        long timestamp,
                                                        @Nullable byte[] signedGroupChange,
                                                        @Nullable String serverGuid)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      return updater.updateLocalToServerRevision(revision, timestamp, groupRecord, groupSecretParams, signedGroupChange, serverGuid);
    }
  }

  @WorkerThread
  public static void forceSanityUpdateFromServer(@NonNull Context context,
                                                 @NonNull GroupMasterKey groupMasterKey,
                                                 long timestamp)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      updater.forceSanityUpdateFromServer(timestamp);
    }
  }

  @WorkerThread
  public static void updateGroupSendEndorsements(@NonNull Context context,
                                                 @NonNull GroupMasterKey groupMasterKey)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      updater.updateGroupSendEndorsements();
    }
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
    if (!SignalDatabase.groups().groupExists(groupId)) {
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
      SignalDatabase.groups().setActive(groupId, true);
    }
  }

  @WorkerThread
  public static void updateGroupTimer(@NonNull Context context, @NonNull GroupId.Push groupId, int expirationTime)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (!groupId.isV2()) {
      throw new GroupChangeFailedException("Not gv2");
    }

    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateGroupTimer(expirationTime);
    }
  }

  @WorkerThread
  public static void revokeInvites(@NonNull Context context,
                                   @NonNull ServiceId authServiceId,
                                   @NonNull GroupId.V2 groupId,
                                   @NonNull Collection<UuidCiphertext> uuidCipherTexts)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.revokeInvites(authServiceId, uuidCipherTexts, true);
    }
  }

  @WorkerThread
  public static void ban(@NonNull Context context,
                         @NonNull GroupId.V2 groupId,
                         @NonNull RecipientId recipientId)
      throws GroupChangeBusyException, IOException, GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException
  {
    GroupTable.V2GroupProperties groupProperties = SignalDatabase.groups().requireGroup(groupId).requireV2GroupProperties();
    Recipient                    recipient       = Recipient.resolved(recipientId);

    if (groupProperties.getBannedMembers().contains(recipient.requireServiceId())) {
      Log.i(TAG, "Attempt to ban already banned recipient: " + recipientId);
      return;
    }

    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.ban(recipient.requireServiceId());
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
  public static void applyAnnouncementGroupChange(@NonNull Context context,
                                                  @NonNull GroupId.V2 groupId,
                                                  boolean isAnnouncementGroup)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateAnnouncementGroup(isAnnouncementGroup);
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
    if (!groupId.isV2()) {
      throw new GroupChangeFailedException("Not gv2");
    }

    GroupRecord groupRecord = SignalDatabase.groups().requireGroup(groupId);

    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      return editor.addMembers(newMembers, groupRecord.requireV2GroupProperties().getBannedMembers());
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
}
