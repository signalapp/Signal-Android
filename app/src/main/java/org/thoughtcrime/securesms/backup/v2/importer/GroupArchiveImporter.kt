/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import android.content.ContentValues
import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.backup.v2.ArchiveGroup
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.conversation.colors.AvatarColorHash
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Handles the importing of [ArchiveGroup] models into the local database.
 */
object GroupArchiveImporter {
  fun import(group: ArchiveGroup): RecipientId {
    val masterKey = GroupMasterKey(group.masterKey.toByteArray())
    val groupId = GroupId.v2(masterKey)

    val operations = AppDependencies.groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(masterKey))
    val decryptedState = if (group.snapshot == null) {
      DecryptedGroup(revision = GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
    } else {
      group.snapshot.toLocal(operations)
    }

    val values = ContentValues().apply {
      put(RecipientTable.GROUP_ID, groupId.toString())
      put(RecipientTable.AVATAR_COLOR, AvatarColorHash.forGroupId(groupId).serialize())
      put(RecipientTable.PROFILE_SHARING, group.whitelisted)
      put(RecipientTable.TYPE, RecipientTable.RecipientType.GV2.id)
      put(RecipientTable.STORAGE_SERVICE_ID, Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
      if (group.hideStory) {
        val extras = RecipientExtras.Builder().hideStory(true).build()
        put(RecipientTable.EXTRAS, extras.encode())
      }
    }

    val recipientId = SignalDatabase.writableDatabase.insert(RecipientTable.TABLE_NAME, null, values)
    val restoredId = SignalDatabase.groups.create(masterKey, decryptedState, groupSendEndorsements = null)
    if (restoredId != null) {
      SignalDatabase.groups.setShowAsStoryState(restoredId, group.storySendMode.toLocal())
    }

    return RecipientId.from(recipientId)
  }
}

private fun Group.StorySendMode.toLocal(): GroupTable.ShowAsStoryState {
  return when (this) {
    Group.StorySendMode.ENABLED -> GroupTable.ShowAsStoryState.ALWAYS
    Group.StorySendMode.DISABLED -> GroupTable.ShowAsStoryState.NEVER
    Group.StorySendMode.DEFAULT -> GroupTable.ShowAsStoryState.IF_ACTIVE
  }
}

private fun Group.MemberPendingProfileKey.toLocal(operations: GroupsV2Operations.GroupOperations): DecryptedPendingMember {
  return DecryptedPendingMember(
    serviceIdBytes = member!!.userId,
    role = member.role.toLocal(),
    addedByAci = addedByUserId,
    timestamp = timestamp,
    serviceIdCipherText = operations.encryptServiceId(ServiceId.Companion.parseOrNull(member.userId))
  )
}

private fun Group.AccessControl.AccessRequired.toLocal(): AccessControl.AccessRequired {
  return when (this) {
    Group.AccessControl.AccessRequired.UNKNOWN -> AccessControl.AccessRequired.UNKNOWN
    Group.AccessControl.AccessRequired.ANY -> AccessControl.AccessRequired.ANY
    Group.AccessControl.AccessRequired.MEMBER -> AccessControl.AccessRequired.MEMBER
    Group.AccessControl.AccessRequired.ADMINISTRATOR -> AccessControl.AccessRequired.ADMINISTRATOR
    Group.AccessControl.AccessRequired.UNSATISFIABLE -> AccessControl.AccessRequired.UNSATISFIABLE
  }
}

private fun Group.AccessControl.toLocal(): AccessControl {
  return AccessControl(members = this.members.toLocal(), attributes = this.attributes.toLocal(), addFromInviteLink = this.addFromInviteLink.toLocal())
}

private fun Group.Member.Role.toLocal(): Member.Role {
  return when (this) {
    Group.Member.Role.UNKNOWN -> Member.Role.UNKNOWN
    Group.Member.Role.DEFAULT -> Member.Role.DEFAULT
    Group.Member.Role.ADMINISTRATOR -> Member.Role.ADMINISTRATOR
  }
}

private fun Group.Member.toLocal(): DecryptedMember {
  return DecryptedMember(aciBytes = userId, role = role.toLocal(), joinedAtRevision = joinedAtVersion)
}

private fun Group.MemberPendingAdminApproval.toLocal(): DecryptedRequestingMember {
  return DecryptedRequestingMember(
    aciBytes = this.userId,
    timestamp = this.timestamp
  )
}

private fun Group.MemberBanned.toLocal(): DecryptedBannedMember {
  return DecryptedBannedMember(
    serviceIdBytes = this.userId,
    timestamp = this.timestamp
  )
}

private fun Group.GroupSnapshot.toLocal(operations: GroupsV2Operations.GroupOperations): DecryptedGroup {
  return DecryptedGroup(
    title = this.title?.title ?: "",
    avatar = this.avatarUrl,
    disappearingMessagesTimer = DecryptedTimer(duration = this.disappearingMessagesTimer?.disappearingMessagesDuration ?: 0),
    accessControl = this.accessControl?.toLocal(),
    revision = this.version,
    members = this.members.map { member -> member.toLocal() },
    pendingMembers = this.membersPendingProfileKey.map { pending -> pending.toLocal(operations) },
    requestingMembers = this.membersPendingAdminApproval.map { requesting -> requesting.toLocal() },
    inviteLinkPassword = this.inviteLinkPassword,
    description = this.description?.descriptionText ?: "",
    isAnnouncementGroup = if (this.announcements_only) EnabledState.ENABLED else EnabledState.DISABLED,
    bannedMembers = this.members_banned.map { it.toLocal() }
  )
}
