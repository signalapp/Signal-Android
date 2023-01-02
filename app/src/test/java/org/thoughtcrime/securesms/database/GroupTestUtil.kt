package org.thoughtcrime.securesms.database

import com.google.protobuf.ByteString
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.GroupChange
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional

fun DecryptedGroupChange.Builder.setNewDescription(description: String) {
  newDescription = DecryptedString.newBuilder().setValue(description).build()
}

fun DecryptedGroupChange.Builder.setNewTitle(title: String) {
  newTitle = DecryptedString.newBuilder().setValue(title).build()
}

class ChangeLog(private val revision: Int) {
  var groupSnapshot: DecryptedGroup? = null
  var groupChange: DecryptedGroupChange? = null

  fun change(init: DecryptedGroupChange.Builder.() -> Unit) {
    val builder = DecryptedGroupChange.newBuilder().setRevision(revision)
    builder.init()
    groupChange = builder.build()
  }

  fun fullSnapshot(
    extendGroup: DecryptedGroup? = null,
    title: String = extendGroup?.title ?: "",
    avatar: String = extendGroup?.avatar ?: "",
    description: String = extendGroup?.description ?: "",
    accessControl: AccessControl = extendGroup?.accessControl ?: AccessControl.getDefaultInstance(),
    members: List<DecryptedMember> = extendGroup?.membersList ?: emptyList(),
    pendingMembers: List<DecryptedPendingMember> = extendGroup?.pendingMembersList ?: emptyList(),
    requestingMembers: List<DecryptedRequestingMember> = extendGroup?.requestingMembersList ?: emptyList(),
    inviteLinkPassword: ByteArray = extendGroup?.inviteLinkPassword?.toByteArray() ?: ByteArray(0),
    disappearingMessageTimer: DecryptedTimer = extendGroup?.disappearingMessagesTimer ?: DecryptedTimer.getDefaultInstance()
  ) {
    groupSnapshot = decryptedGroup(revision, title, avatar, description, accessControl, members, pendingMembers, requestingMembers, inviteLinkPassword, disappearingMessageTimer)
  }
}

class ChangeSet {
  private val changeSet: MutableList<ChangeLog> = mutableListOf()

  fun changeLog(revision: Int, init: ChangeLog.() -> Unit) {
    val entry = ChangeLog(revision)
    entry.init()
    changeSet += entry
  }

  fun toApiResponse(): GroupHistoryPage {
    return GroupHistoryPage(changeSet.map { DecryptedGroupHistoryEntry(Optional.ofNullable(it.groupSnapshot), Optional.ofNullable(it.groupChange)) }, GroupHistoryPage.PagingData.NONE)
  }
}

class GroupChangeData(private val revision: Int, private val groupOperations: GroupsV2Operations.GroupOperations) {
  private val groupChangeBuilder: GroupChange.Builder = GroupChange.newBuilder()
  private val actionsBuilder: GroupChange.Actions.Builder = GroupChange.Actions.newBuilder()
  var changeEpoch: Int = GroupsV2Operations.HIGHEST_KNOWN_EPOCH

  val groupChange: GroupChange
    get() {
      return groupChangeBuilder
        .setChangeEpoch(changeEpoch)
        .setActions(actionsBuilder.setRevision(revision).build().toByteString())
        .build()
    }

  fun source(serviceId: ServiceId) {
    actionsBuilder.sourceUuid = groupOperations.encryptUuid(serviceId.uuid())
  }

  fun deleteMember(serviceId: ServiceId) {
    actionsBuilder.addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder().setDeletedUserId(groupOperations.encryptUuid(serviceId.uuid())))
  }

  fun modifyRole(serviceId: ServiceId, role: Member.Role) {
    actionsBuilder.addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(groupOperations.encryptUuid(serviceId.uuid())).setRole(role))
  }
}

class GroupStateTestData(private val masterKey: GroupMasterKey, private val groupOperations: GroupsV2Operations.GroupOperations? = null) {

  var localState: DecryptedGroup? = null
  var groupRecord: Optional<GroupRecord> = Optional.empty()
  var serverState: DecryptedGroup? = null
  var changeSet: ChangeSet? = null
  var groupChange: GroupChange? = null
  var includeFirst: Boolean = false
  var requestedRevision: Int = 0

  fun localState(
    active: Boolean = true,
    revision: Int = 0,
    title: String = "",
    avatar: String = "",
    description: String = "",
    accessControl: AccessControl = AccessControl.getDefaultInstance(),
    members: List<DecryptedMember> = emptyList(),
    pendingMembers: List<DecryptedPendingMember> = emptyList(),
    requestingMembers: List<DecryptedRequestingMember> = emptyList(),
    inviteLinkPassword: ByteArray = ByteArray(0),
    disappearingMessageTimer: DecryptedTimer = DecryptedTimer.getDefaultInstance()
  ) {
    localState = decryptedGroup(revision, title, avatar, description, accessControl, members, pendingMembers, requestingMembers, inviteLinkPassword, disappearingMessageTimer)
    groupRecord = groupRecord(masterKey, localState!!, active = active)
  }

  fun serverState(
    revision: Int,
    extendGroup: DecryptedGroup? = null,
    title: String = extendGroup?.title ?: "",
    avatar: String = extendGroup?.avatar ?: "",
    description: String = extendGroup?.description ?: "",
    accessControl: AccessControl = extendGroup?.accessControl ?: AccessControl.getDefaultInstance(),
    members: List<DecryptedMember> = extendGroup?.membersList ?: emptyList(),
    pendingMembers: List<DecryptedPendingMember> = extendGroup?.pendingMembersList ?: emptyList(),
    requestingMembers: List<DecryptedRequestingMember> = extendGroup?.requestingMembersList ?: emptyList(),
    inviteLinkPassword: ByteArray = extendGroup?.inviteLinkPassword?.toByteArray() ?: ByteArray(0),
    disappearingMessageTimer: DecryptedTimer = extendGroup?.disappearingMessagesTimer ?: DecryptedTimer.getDefaultInstance()
  ) {
    serverState = decryptedGroup(revision, title, avatar, description, accessControl, members, pendingMembers, requestingMembers, inviteLinkPassword, disappearingMessageTimer)
  }

  fun changeSet(init: ChangeSet.() -> Unit) {
    val changeSet = ChangeSet()
    changeSet.init()
    this.changeSet = changeSet
  }

  fun apiCallParameters(requestedRevision: Int, includeFirst: Boolean) {
    this.requestedRevision = requestedRevision
    this.includeFirst = includeFirst
  }

  fun groupChange(revision: Int, init: GroupChangeData.() -> Unit) {
    val groupChangeData = GroupChangeData(revision, groupOperations!!)
    groupChangeData.init()
    this.groupChange = groupChangeData.groupChange
  }
}

fun groupRecord(
  masterKey: GroupMasterKey,
  decryptedGroup: DecryptedGroup,
  id: GroupId = GroupId.v2(masterKey),
  recipientId: RecipientId = RecipientId.from(100),
  members: String = "1",
  unmigratedV1Members: String? = null,
  avatarId: Long = 1,
  avatarKey: ByteArray = ByteArray(0),
  avatarContentType: String = "",
  relay: String = "",
  active: Boolean = true,
  avatarDigest: ByteArray = ByteArray(0),
  mms: Boolean = false,
  distributionId: DistributionId? = null
): Optional<GroupRecord> {
  return Optional.of(
    GroupRecord(
      id,
      recipientId,
      decryptedGroup.title,
      members,
      unmigratedV1Members,
      avatarId,
      avatarKey,
      avatarContentType,
      relay,
      active,
      avatarDigest,
      mms,
      masterKey.serialize(),
      decryptedGroup.revision,
      decryptedGroup.toByteArray(),
      distributionId,
      System.currentTimeMillis()
    )
  )
}

fun decryptedGroup(
  revision: Int = 0,
  title: String = "",
  avatar: String = "",
  description: String = "",
  accessControl: AccessControl = AccessControl.getDefaultInstance(),
  members: List<DecryptedMember> = emptyList(),
  pendingMembers: List<DecryptedPendingMember> = emptyList(),
  requestingMembers: List<DecryptedRequestingMember> = emptyList(),
  inviteLinkPassword: ByteArray = ByteArray(0),
  disappearingMessageTimer: DecryptedTimer = DecryptedTimer.getDefaultInstance()
): DecryptedGroup {

  val builder = DecryptedGroup.newBuilder()
    .setAccessControl(accessControl)
    .setAvatar(avatar)
    .setAvatarBytes(ByteString.EMPTY)
    .setDescription(description)
    .setDisappearingMessagesTimer(disappearingMessageTimer)
    .setInviteLinkPassword(ByteString.copyFrom(inviteLinkPassword))
    .setIsAnnouncementGroup(EnabledState.DISABLED)
    .setTitle(title)
    .setRevision(revision)
    .addAllMembers(members)
    .addAllPendingMembers(pendingMembers)
    .addAllRequestingMembers(requestingMembers)

  return builder.build()
}
