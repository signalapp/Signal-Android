/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.content.ContentValues
import android.database.Cursor
import androidx.core.content.contentValuesOf
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.deleteAll
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.libsignal.zkgroup.InvalidInputException
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
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.backup.v2.proto.Self
import org.thoughtcrime.securesms.conversation.colors.AvatarColorHash
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTableCursorUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.Closeable

typealias BackupRecipient = org.thoughtcrime.securesms.backup.v2.proto.Recipient
typealias BackupGroup = Group

/**
 * Fetches all individual contacts for backups and returns the result as an iterator.
 * It's important to note that the iterator still needs to be closed after it's used.
 * It's recommended to use `.use` or a try-with-resources pattern.
 */
fun RecipientTable.getContactsForBackup(selfId: Long): BackupContactIterator {
  val cursor = readableDatabase
    .select(
      RecipientTable.ID,
      RecipientTable.ACI_COLUMN,
      RecipientTable.PNI_COLUMN,
      RecipientTable.USERNAME,
      RecipientTable.E164,
      RecipientTable.BLOCKED,
      RecipientTable.HIDDEN,
      RecipientTable.REGISTERED,
      RecipientTable.UNREGISTERED_TIMESTAMP,
      RecipientTable.PROFILE_KEY,
      RecipientTable.PROFILE_SHARING,
      RecipientTable.PROFILE_GIVEN_NAME,
      RecipientTable.PROFILE_FAMILY_NAME,
      RecipientTable.PROFILE_JOINED_NAME,
      RecipientTable.MUTE_UNTIL,
      RecipientTable.CHAT_COLORS,
      RecipientTable.CUSTOM_CHAT_COLORS_ID,
      RecipientTable.EXTRAS
    )
    .from(RecipientTable.TABLE_NAME)
    .where(
      """
      ${RecipientTable.TYPE} = ? AND (
        ${RecipientTable.ACI_COLUMN} NOT NULL OR
        ${RecipientTable.PNI_COLUMN} NOT NULL OR
        ${RecipientTable.E164} NOT NULL
      )
      """,
      RecipientTable.RecipientType.INDIVIDUAL.id
    )
    .run()

  return BackupContactIterator(cursor, selfId)
}

fun RecipientTable.getGroupsForBackup(): BackupGroupIterator {
  val cursor = readableDatabase
    .select(
      "${RecipientTable.TABLE_NAME}.${RecipientTable.ID}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_SHARING}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.EXTRAS}",
      "${GroupTable.TABLE_NAME}.${GroupTable.V2_MASTER_KEY}",
      "${GroupTable.TABLE_NAME}.${GroupTable.SHOW_AS_STORY_STATE}",
      "${GroupTable.TABLE_NAME}.${GroupTable.TITLE}",
      "${GroupTable.TABLE_NAME}.${GroupTable.V2_DECRYPTED_GROUP}"
    )
    .from(
      """
      ${RecipientTable.TABLE_NAME} 
        INNER JOIN ${GroupTable.TABLE_NAME} ON ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} = ${GroupTable.TABLE_NAME}.${GroupTable.RECIPIENT_ID}
      """
    )
    .where("${GroupTable.TABLE_NAME}.${GroupTable.V2_MASTER_KEY} IS NOT NULL")
    .run()

  return BackupGroupIterator(cursor)
}

/**
 * Given [AccountData], this will insert the necessary data for the local user into the [RecipientTable].
 */
fun RecipientTable.restoreSelfFromBackup(accountData: AccountData, selfId: RecipientId) {
  val values = ContentValues().apply {
    put(RecipientTable.PROFILE_GIVEN_NAME, accountData.givenName.nullIfBlank())
    put(RecipientTable.PROFILE_FAMILY_NAME, accountData.familyName.nullIfBlank())
    put(RecipientTable.PROFILE_JOINED_NAME, ProfileName.fromParts(accountData.givenName, accountData.familyName).toString().nullIfBlank())
    put(RecipientTable.PROFILE_AVATAR, accountData.avatarUrlPath.nullIfBlank())
    put(RecipientTable.REGISTERED, RecipientTable.RegisteredState.REGISTERED.id)
    put(RecipientTable.PROFILE_SHARING, true)
    put(RecipientTable.UNREGISTERED_TIMESTAMP, 0)
    put(RecipientTable.EXTRAS, RecipientExtras().encode())

    try {
      put(RecipientTable.PROFILE_KEY, Base64.encodeWithPadding(accountData.profileKey.toByteArray()).nullIfBlank())
    } catch (e: InvalidInputException) {
      Log.w(TAG, "Missing profile key during restore")
    }

    put(RecipientTable.USERNAME, accountData.username)
  }

  writableDatabase
    .update(RecipientTable.TABLE_NAME)
    .values(values)
    .where("${RecipientTable.ID} = ?", selfId)
    .run()
}

fun RecipientTable.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(RecipientTable.TABLE_NAME)
  SqlUtil.resetAutoIncrementValue(writableDatabase, RecipientTable.TABLE_NAME)

  RecipientId.clearCache()
  AppDependencies.recipientCache.clear()
  AppDependencies.recipientCache.clearSelf()
}

fun RecipientTable.restoreContactFromBackup(contact: Contact): RecipientId {
  val id = getAndPossiblyMergePnpVerified(
    aci = ACI.parseOrNull(contact.aci?.toByteArray()),
    pni = PNI.parseOrNull(contact.pni?.toByteArray()),
    e164 = contact.formattedE164
  )

  val profileKey = contact.profileKey?.toByteArray()
  val values = contentValuesOf(
    RecipientTable.BLOCKED to contact.blocked,
    RecipientTable.HIDDEN to (contact.visibility == Contact.Visibility.HIDDEN),
    RecipientTable.TYPE to RecipientTable.RecipientType.INDIVIDUAL.id,
    RecipientTable.PROFILE_FAMILY_NAME to contact.profileFamilyName.nullIfBlank(),
    RecipientTable.PROFILE_GIVEN_NAME to contact.profileGivenName.nullIfBlank(),
    RecipientTable.PROFILE_JOINED_NAME to ProfileName.fromParts(contact.profileGivenName.nullIfBlank(), contact.profileFamilyName.nullIfBlank()).toString().nullIfBlank(),
    RecipientTable.PROFILE_KEY to if (profileKey == null) null else Base64.encodeWithPadding(profileKey),
    RecipientTable.PROFILE_SHARING to contact.profileSharing.toInt(),
    RecipientTable.USERNAME to contact.username,
    RecipientTable.EXTRAS to contact.toLocalExtras().encode()
  )

  if (contact.registered != null) {
    values.put(RecipientTable.UNREGISTERED_TIMESTAMP, 0L)
    values.put(RecipientTable.REGISTERED, RecipientTable.RegisteredState.REGISTERED.id)
  } else if (contact.notRegistered != null) {
    values.put(RecipientTable.UNREGISTERED_TIMESTAMP, contact.notRegistered.unregisteredTimestamp)
    values.put(RecipientTable.REGISTERED, RecipientTable.RegisteredState.NOT_REGISTERED.id)
  }

  writableDatabase
    .update(RecipientTable.TABLE_NAME)
    .values(values)
    .where("${RecipientTable.ID} = ?", id)
    .run()

  return id
}

fun RecipientTable.restoreReleaseNotes(): RecipientId {
  val releaseChannelId: RecipientId = insertReleaseChannelRecipient()
  SignalStore.releaseChannel.setReleaseChannelRecipientId(releaseChannelId)

  setProfileName(releaseChannelId, ProfileName.asGiven("Signal"))
  setMuted(releaseChannelId, Long.MAX_VALUE)
  return releaseChannelId
}

fun RecipientTable.restoreGroupFromBackup(group: Group): RecipientId {
  val masterKey = GroupMasterKey(group.masterKey.toByteArray())
  val groupId = GroupId.v2(masterKey)

  val operations = AppDependencies.groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(masterKey))
  val decryptedState = if (group.snapshot == null) {
    DecryptedGroup(revision = GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
  } else {
    group.snapshot.toDecryptedGroup(operations)
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

  val recipientId = writableDatabase.insert(RecipientTable.TABLE_NAME, null, values)
  val restoredId = SignalDatabase.groups.create(masterKey, decryptedState, groupSendEndorsements = null)
  if (restoredId != null) {
    SignalDatabase.groups.setShowAsStoryState(restoredId, group.storySendMode.toGroupShowAsStoryState())
  }

  return RecipientId.from(recipientId)
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

private fun AccessControl.AccessRequired.toSnapshot(): Group.AccessControl.AccessRequired {
  return when (this) {
    AccessControl.AccessRequired.UNKNOWN -> Group.AccessControl.AccessRequired.UNKNOWN
    AccessControl.AccessRequired.ANY -> Group.AccessControl.AccessRequired.ANY
    AccessControl.AccessRequired.MEMBER -> Group.AccessControl.AccessRequired.MEMBER
    AccessControl.AccessRequired.ADMINISTRATOR -> Group.AccessControl.AccessRequired.ADMINISTRATOR
    AccessControl.AccessRequired.UNSATISFIABLE -> Group.AccessControl.AccessRequired.UNSATISFIABLE
  }
}

private fun AccessControl.toSnapshot(): Group.AccessControl {
  return Group.AccessControl(members = members.toSnapshot(), attributes = attributes.toSnapshot(), addFromInviteLink = addFromInviteLink.toSnapshot())
}

private fun Member.Role.toSnapshot(): Group.Member.Role {
  return when (this) {
    Member.Role.UNKNOWN -> Group.Member.Role.UNKNOWN
    Member.Role.DEFAULT -> Group.Member.Role.DEFAULT
    Member.Role.ADMINISTRATOR -> Group.Member.Role.ADMINISTRATOR
  }
}

private fun DecryptedGroup.toSnapshot(): Group.GroupSnapshot? {
  if (revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION || revision == GroupsV2StateProcessor.PLACEHOLDER_REVISION) {
    return null
  }
  return Group.GroupSnapshot(
    title = Group.GroupAttributeBlob(title = title),
    avatarUrl = avatar,
    disappearingMessagesTimer = Group.GroupAttributeBlob(disappearingMessagesDuration = disappearingMessagesTimer?.duration ?: 0),
    accessControl = accessControl?.toSnapshot(),
    version = revision,
    members = members.map { it.toSnapshot() },
    membersPendingProfileKey = pendingMembers.map { it.toSnapshot() },
    membersPendingAdminApproval = requestingMembers.map { it.toSnapshot() },
    inviteLinkPassword = inviteLinkPassword,
    description = Group.GroupAttributeBlob(descriptionText = description),
    announcements_only = isAnnouncementGroup == EnabledState.ENABLED,
    members_banned = bannedMembers.map { it.toSnapshot() }
  )
}

private fun Group.Member.toLocal(): DecryptedMember {
  return DecryptedMember(aciBytes = userId, role = role.toLocal(), profileKey = profileKey, joinedAtRevision = joinedAtVersion)
}

private fun DecryptedMember.toSnapshot(): Group.Member {
  return Group.Member(userId = aciBytes, role = role.toSnapshot(), profileKey = profileKey, joinedAtVersion = joinedAtRevision)
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

private fun DecryptedPendingMember.toSnapshot(): Group.MemberPendingProfileKey {
  return Group.MemberPendingProfileKey(
    member = Group.Member(
      userId = serviceIdBytes,
      role = role.toSnapshot()
    ),
    addedByUserId = addedByAci,
    timestamp = timestamp
  )
}

private fun Group.MemberPendingAdminApproval.toLocal(): DecryptedRequestingMember {
  return DecryptedRequestingMember(
    aciBytes = userId,
    profileKey = profileKey,
    timestamp = timestamp
  )
}

private fun DecryptedRequestingMember.toSnapshot(): Group.MemberPendingAdminApproval {
  return Group.MemberPendingAdminApproval(
    userId = aciBytes,
    profileKey = profileKey,
    timestamp = timestamp
  )
}

private fun Group.MemberBanned.toLocal(): DecryptedBannedMember {
  return DecryptedBannedMember(
    serviceIdBytes = userId,
    timestamp = timestamp
  )
}

private fun DecryptedBannedMember.toSnapshot(): Group.MemberBanned {
  return Group.MemberBanned(
    userId = serviceIdBytes,
    timestamp = timestamp
  )
}

private fun Group.GroupSnapshot.toDecryptedGroup(operations: GroupsV2Operations.GroupOperations): DecryptedGroup {
  return DecryptedGroup(
    title = title?.title ?: "",
    avatar = avatarUrl,
    disappearingMessagesTimer = DecryptedTimer(duration = disappearingMessagesTimer?.disappearingMessagesDuration ?: 0),
    accessControl = accessControl?.toLocal(),
    revision = version,
    members = members.map { member -> member.toLocal() },
    pendingMembers = membersPendingProfileKey.map { pending -> pending.toLocal(operations) },
    requestingMembers = membersPendingAdminApproval.map { requesting -> requesting.toLocal() },
    inviteLinkPassword = inviteLinkPassword,
    description = description?.descriptionText ?: "",
    isAnnouncementGroup = if (announcements_only) EnabledState.ENABLED else EnabledState.DISABLED,
    bannedMembers = members_banned.map { it.toLocal() }
  )
}

private fun Contact.toLocalExtras(): RecipientExtras {
  return RecipientExtras(
    hideStory = this.hideStory
  )
}

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class BackupContactIterator(private val cursor: Cursor, private val selfId: Long) : Iterator<BackupRecipient?>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): BackupRecipient? {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val id = cursor.requireLong(RecipientTable.ID)
    if (id == selfId) {
      return BackupRecipient(
        id = id,
        self = Self()
      )
    }

    val aci = ACI.parseOrNull(cursor.requireString(RecipientTable.ACI_COLUMN))
    val pni = PNI.parseOrNull(cursor.requireString(RecipientTable.PNI_COLUMN))
    val e164 = cursor.requireString(RecipientTable.E164)?.e164ToLong()
    val registeredState = RecipientTable.RegisteredState.fromId(cursor.requireInt(RecipientTable.REGISTERED))
    val profileKey = cursor.requireString(RecipientTable.PROFILE_KEY)
    val extras = RecipientTableCursorUtil.getExtras(cursor)

    if (aci == null && pni == null && e164 == null) {
      return null
    }

    val contactBuilder = Contact.Builder()
      .aci(aci?.rawUuid?.toByteArray()?.toByteString())
      .pni(pni?.rawUuid?.toByteArray()?.toByteString())
      .username(cursor.requireString(RecipientTable.USERNAME))
      .e164(cursor.requireString(RecipientTable.E164)?.e164ToLong())
      .blocked(cursor.requireBoolean(RecipientTable.BLOCKED))
      .visibility(if (cursor.requireBoolean(RecipientTable.HIDDEN)) Contact.Visibility.HIDDEN else Contact.Visibility.VISIBLE)
      .profileKey(if (profileKey != null) Base64.decode(profileKey).toByteString() else null)
      .profileSharing(cursor.requireBoolean(RecipientTable.PROFILE_SHARING))
      .profileGivenName(cursor.requireString(RecipientTable.PROFILE_GIVEN_NAME).nullIfBlank())
      .profileFamilyName(cursor.requireString(RecipientTable.PROFILE_FAMILY_NAME).nullIfBlank())
      .hideStory(extras?.hideStory() ?: false)

    if (registeredState == RecipientTable.RegisteredState.REGISTERED) {
      contactBuilder.registered = Contact.Registered()
    } else {
      contactBuilder.notRegistered = Contact.NotRegistered(unregisteredTimestamp = cursor.requireLong(RecipientTable.UNREGISTERED_TIMESTAMP))
    }

    return BackupRecipient(
      id = id,
      contact = contactBuilder.build()
    )
  }

  override fun close() {
    cursor.close()
  }
}

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class BackupGroupIterator(private val cursor: Cursor) : Iterator<BackupRecipient>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): BackupRecipient {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val extras = RecipientTableCursorUtil.getExtras(cursor)
    val showAsStoryState: GroupTable.ShowAsStoryState = GroupTable.ShowAsStoryState.deserialize(cursor.requireInt(GroupTable.SHOW_AS_STORY_STATE))

    val decryptedGroup: DecryptedGroup = DecryptedGroup.ADAPTER.decode(cursor.requireBlob(GroupTable.V2_DECRYPTED_GROUP)!!)

    return BackupRecipient(
      id = cursor.requireLong(RecipientTable.ID),
      group = BackupGroup(
        masterKey = cursor.requireNonNullBlob(GroupTable.V2_MASTER_KEY).toByteString(),
        whitelisted = cursor.requireBoolean(RecipientTable.PROFILE_SHARING),
        hideStory = extras?.hideStory() ?: false,
        storySendMode = showAsStoryState.toGroupStorySendMode(),
        snapshot = decryptedGroup.toSnapshot()
      )
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun String.e164ToLong(): Long? {
  val fixed = if (this.startsWith("+")) {
    this.substring(1)
  } else {
    this
  }

  return fixed.toLongOrNull()
}

private fun GroupTable.ShowAsStoryState.toGroupStorySendMode(): Group.StorySendMode {
  return when (this) {
    GroupTable.ShowAsStoryState.ALWAYS -> Group.StorySendMode.ENABLED
    GroupTable.ShowAsStoryState.NEVER -> Group.StorySendMode.DISABLED
    GroupTable.ShowAsStoryState.IF_ACTIVE -> Group.StorySendMode.DEFAULT
  }
}

private fun Group.StorySendMode.toGroupShowAsStoryState(): GroupTable.ShowAsStoryState {
  return when (this) {
    Group.StorySendMode.ENABLED -> GroupTable.ShowAsStoryState.ALWAYS
    Group.StorySendMode.DISABLED -> GroupTable.ShowAsStoryState.NEVER
    Group.StorySendMode.DEFAULT -> GroupTable.ShowAsStoryState.IF_ACTIVE
  }
}

private val Contact.formattedE164: String?
  get() {
    return e164?.let {
      PhoneNumberFormatter.get(AppDependencies.application).format(e164.toString())
    }
  }
