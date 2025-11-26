/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportSkips
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.NotificationProfileTables.NotificationProfileAllowedMembersTable
import org.thoughtcrime.securesms.database.NotificationProfileTables.NotificationProfileScheduleTable
import org.thoughtcrime.securesms.database.NotificationProfileTables.NotificationProfileTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.serialize
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.util.UuidUtil
import java.time.DayOfWeek
import org.thoughtcrime.securesms.backup.v2.proto.NotificationProfile as NotificationProfileProto

/**
 * Handles exporting and importing [NotificationProfile] models.
 */
object NotificationProfileArchiveProcessor {

  private val TAG = Log.tag(NotificationProfileArchiveProcessor::class)

  fun export(db: SignalDatabase, exportState: ExportState, emitter: BackupFrameEmitter) {
    db.notificationProfileTables
      .getProfiles()
      .forEach { profile ->
        val frame = profile.toBackupFrame(includeRecipient = { id -> exportState.recipientIds.contains(id.toLong()) && id != exportState.selfRecipientId })
        emitter.emit(frame)
      }
  }

  fun import(profile: NotificationProfileProto, importState: ImportState) {
    val notificationProfileUuid = UuidUtil.parseOrNull(profile.id)
    if (notificationProfileUuid == null) {
      ImportSkips.notificationProfileIdNotFound()
      return
    }

    val profileId = SignalDatabase
      .writableDatabase
      .insertInto(NotificationProfileTable.TABLE_NAME)
      .values(
        NotificationProfileTable.NAME to profile.name,
        NotificationProfileTable.EMOJI to (profile.emoji ?: ""),
        NotificationProfileTable.COLOR to (AvatarColor.fromColor(profile.color) ?: AvatarColor.random()).serialize(),
        NotificationProfileTable.CREATED_AT to profile.createdAtMs,
        NotificationProfileTable.ALLOW_ALL_CALLS to profile.allowAllCalls.toInt(),
        NotificationProfileTable.ALLOW_ALL_MENTIONS to profile.allowAllMentions.toInt(),
        NotificationProfileTable.NOTIFICATION_PROFILE_ID to notificationProfileUuid.toString(),
        NotificationProfileTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )
      .run()

    if (profileId < 0) {
      Log.w(TAG, "Notification profile name already exists")
      return
    }

    SignalDatabase
      .writableDatabase
      .insertInto(NotificationProfileScheduleTable.TABLE_NAME)
      .values(
        NotificationProfileScheduleTable.NOTIFICATION_PROFILE_ID to profileId,
        NotificationProfileScheduleTable.ENABLED to profile.scheduleEnabled.toInt(),
        NotificationProfileScheduleTable.START to profile.scheduleStartTime,
        NotificationProfileScheduleTable.END to profile.scheduleEndTime,
        NotificationProfileScheduleTable.DAYS_ENABLED to profile.scheduleDaysEnabled.map { it.toLocal() }.toSet().serialize()
      )
      .run()

    profile
      .allowedMembers
      .mapNotNull { importState.remoteToLocalRecipientId[it] }
      .forEach { recipientId ->
        SignalDatabase
          .writableDatabase
          .insertInto(NotificationProfileAllowedMembersTable.TABLE_NAME)
          .values(
            NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID to profileId,
            NotificationProfileAllowedMembersTable.RECIPIENT_ID to recipientId.serialize()
          )
          .run()
      }
  }
}

private fun NotificationProfile.toBackupFrame(includeRecipient: (RecipientId) -> Boolean): Frame {
  val profile = NotificationProfileProto(
    id = UuidUtil.toByteArray(this.notificationProfileId.uuid).toByteString(),
    name = this.name,
    emoji = this.emoji.takeIf { it.isNotBlank() },
    color = this.color.colorInt(),
    createdAtMs = this.createdAt,
    allowAllCalls = this.allowAllCalls,
    allowAllMentions = this.allowAllMentions,
    allowedMembers = this.allowedMembers.filter { includeRecipient(it) }.map { it.toLong() },
    scheduleEnabled = this.schedule.enabled,
    scheduleStartTime = this.schedule.start,
    scheduleEndTime = this.schedule.end,
    scheduleDaysEnabled = this.schedule.daysEnabled.map { it.toBackupProto() }
  )

  return Frame(notificationProfile = profile)
}

private fun DayOfWeek.toBackupProto(): NotificationProfileProto.DayOfWeek {
  return when (this) {
    DayOfWeek.MONDAY -> NotificationProfileProto.DayOfWeek.MONDAY
    DayOfWeek.TUESDAY -> NotificationProfileProto.DayOfWeek.TUESDAY
    DayOfWeek.WEDNESDAY -> NotificationProfileProto.DayOfWeek.WEDNESDAY
    DayOfWeek.THURSDAY -> NotificationProfileProto.DayOfWeek.THURSDAY
    DayOfWeek.FRIDAY -> NotificationProfileProto.DayOfWeek.FRIDAY
    DayOfWeek.SATURDAY -> NotificationProfileProto.DayOfWeek.SATURDAY
    DayOfWeek.SUNDAY -> NotificationProfileProto.DayOfWeek.SUNDAY
  }
}

private fun NotificationProfileProto.DayOfWeek.toLocal(): DayOfWeek {
  return when (this) {
    NotificationProfileProto.DayOfWeek.UNKNOWN -> throw IllegalStateException()
    NotificationProfileProto.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
    NotificationProfileProto.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
    NotificationProfileProto.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
    NotificationProfileProto.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
    NotificationProfileProto.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
    NotificationProfileProto.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
    NotificationProfileProto.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
  }
}
