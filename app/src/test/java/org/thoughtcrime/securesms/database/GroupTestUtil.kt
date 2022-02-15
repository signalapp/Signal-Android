package org.thoughtcrime.securesms.database

import com.google.protobuf.ByteString
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.signal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.DistributionId

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
    return GroupHistoryPage(changeSet.map { DecryptedGroupHistoryEntry(Optional.fromNullable(it.groupSnapshot), Optional.fromNullable(it.groupChange)) }, GroupHistoryPage.PagingData.NONE)
  }
}

class GroupStateTestData(private val masterKey: GroupMasterKey) {

  var localState: DecryptedGroup? = null
  var groupRecord: Optional<GroupDatabase.GroupRecord> = Optional.absent()
  var serverState: DecryptedGroup? = null
  var changeSet: ChangeSet? = null
  var includeFirst: Boolean = false
  var requestedRevision: Int = 0

  fun localState(
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
    groupRecord = groupRecord(masterKey, localState!!)
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
): Optional<GroupDatabase.GroupRecord> {
  return Optional.of(
    GroupDatabase.GroupRecord(
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
      distributionId
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

fun member(aci: ACI, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0): DecryptedMember {
  return DecryptedMember.newBuilder()
    .setRole(role)
    .setUuid(aci.toByteString())
    .setJoinedAtRevision(joinedAt)
    .build()
}

fun requestingMember(aci: ACI): DecryptedRequestingMember {
  return DecryptedRequestingMember.newBuilder()
    .setUuid(aci.toByteString())
    .build()
}
