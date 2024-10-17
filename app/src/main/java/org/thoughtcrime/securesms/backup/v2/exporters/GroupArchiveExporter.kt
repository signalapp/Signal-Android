/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.database.Cursor
import okio.ByteString.Companion.toByteString
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.backup.v2.ArchiveGroup
import org.thoughtcrime.securesms.backup.v2.ArchiveRecipient
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTableCursorUtil
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import java.io.Closeable

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [ArchiveRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class GroupArchiveExporter(private val cursor: Cursor) : Iterator<ArchiveRecipient>, Closeable {

  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): ArchiveRecipient {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val extras = RecipientTableCursorUtil.getExtras(cursor)
    val showAsStoryState: GroupTable.ShowAsStoryState = GroupTable.ShowAsStoryState.deserialize(cursor.requireInt(GroupTable.SHOW_AS_STORY_STATE))

    val decryptedGroup: DecryptedGroup = DecryptedGroup.ADAPTER.decode(cursor.requireBlob(GroupTable.V2_DECRYPTED_GROUP)!!)

    return ArchiveRecipient(
      id = cursor.requireLong(RecipientTable.ID),
      group = ArchiveGroup(
        masterKey = cursor.requireNonNullBlob(GroupTable.V2_MASTER_KEY).toByteString(),
        whitelisted = cursor.requireBoolean(RecipientTable.PROFILE_SHARING),
        hideStory = extras?.hideStory() ?: false,
        storySendMode = showAsStoryState.toRemote(),
        snapshot = decryptedGroup.toRemote()
      )
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun GroupTable.ShowAsStoryState.toRemote(): Group.StorySendMode {
  return when (this) {
    GroupTable.ShowAsStoryState.ALWAYS -> Group.StorySendMode.ENABLED
    GroupTable.ShowAsStoryState.NEVER -> Group.StorySendMode.DISABLED
    GroupTable.ShowAsStoryState.IF_ACTIVE -> Group.StorySendMode.DEFAULT
  }
}

private fun DecryptedGroup.toRemote(): Group.GroupSnapshot? {
  if (this.revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION || this.revision == GroupsV2StateProcessor.PLACEHOLDER_REVISION) {
    return null
  }

  return Group.GroupSnapshot(
    title = Group.GroupAttributeBlob(title = this.title),
    avatarUrl = this.avatar,
    disappearingMessagesTimer = this.disappearingMessagesTimer?.takeIf { it.duration > 0 }?.let { Group.GroupAttributeBlob(disappearingMessagesDuration = it.duration) },
    accessControl = this.accessControl?.toRemote(),
    version = this.revision,
    members = this.members.map { it.toRemote() },
    membersPendingProfileKey = this.pendingMembers.map { it.toRemote() },
    membersPendingAdminApproval = this.requestingMembers.map { it.toRemote() },
    inviteLinkPassword = this.inviteLinkPassword,
    description = this.description.takeUnless { it.isBlank() }?.let { Group.GroupAttributeBlob(descriptionText = it) },
    announcements_only = this.isAnnouncementGroup == EnabledState.ENABLED,
    members_banned = this.bannedMembers.map { it.toRemote() }
  )
}

private fun AccessControl.AccessRequired.toRemote(): Group.AccessControl.AccessRequired {
  return when (this) {
    AccessControl.AccessRequired.UNKNOWN -> Group.AccessControl.AccessRequired.UNKNOWN
    AccessControl.AccessRequired.ANY -> Group.AccessControl.AccessRequired.ANY
    AccessControl.AccessRequired.MEMBER -> Group.AccessControl.AccessRequired.MEMBER
    AccessControl.AccessRequired.ADMINISTRATOR -> Group.AccessControl.AccessRequired.ADMINISTRATOR
    AccessControl.AccessRequired.UNSATISFIABLE -> Group.AccessControl.AccessRequired.UNSATISFIABLE
  }
}

private fun AccessControl.toRemote(): Group.AccessControl {
  return Group.AccessControl(members = members.toRemote(), attributes = attributes.toRemote(), addFromInviteLink = addFromInviteLink.toRemote())
}

private fun Member.Role.toRemote(): Group.Member.Role {
  return when (this) {
    Member.Role.UNKNOWN -> Group.Member.Role.UNKNOWN
    Member.Role.DEFAULT -> Group.Member.Role.DEFAULT
    Member.Role.ADMINISTRATOR -> Group.Member.Role.ADMINISTRATOR
  }
}

private fun DecryptedMember.toRemote(): Group.Member {
  return Group.Member(userId = aciBytes, role = role.toRemote(), joinedAtVersion = joinedAtRevision)
}

private fun DecryptedPendingMember.toRemote(): Group.MemberPendingProfileKey {
  return Group.MemberPendingProfileKey(
    member = Group.Member(
      userId = this.serviceIdBytes,
      role = this.role.toRemote()
    ),
    addedByUserId = this.addedByAci,
    timestamp = this.timestamp
  )
}

private fun DecryptedBannedMember.toRemote(): Group.MemberBanned {
  return Group.MemberBanned(
    userId = this.serviceIdBytes,
    timestamp = this.timestamp
  )
}

private fun DecryptedRequestingMember.toRemote(): Group.MemberPendingAdminApproval {
  return Group.MemberPendingAdminApproval(
    userId = this.aciBytes,
    timestamp = this.timestamp
  )
}
