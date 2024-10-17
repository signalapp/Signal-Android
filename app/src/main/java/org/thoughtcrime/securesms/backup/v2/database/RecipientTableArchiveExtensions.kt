/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.content.ContentValues
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.libsignal.zkgroup.InvalidInputException
import org.thoughtcrime.securesms.backup.v2.exporters.ContactArchiveExporter
import org.thoughtcrime.securesms.backup.v2.exporters.GroupArchiveExporter
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Fetches all individual contacts for backups and returns the result as an iterator.
 * It's important to note that the iterator still needs to be closed after it's used.
 * It's recommended to use `.use` or a try-with-resources pattern.
 */
fun RecipientTable.getContactsForBackup(selfId: Long): ContactArchiveExporter {
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

  return ContactArchiveExporter(cursor, selfId)
}

fun RecipientTable.getGroupsForBackup(): GroupArchiveExporter {
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

  return GroupArchiveExporter(cursor)
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

fun RecipientTable.restoreReleaseNotes(): RecipientId {
  val releaseChannelId: RecipientId = insertReleaseChannelRecipient()
  SignalStore.releaseChannel.setReleaseChannelRecipientId(releaseChannelId)

  setProfileName(releaseChannelId, ProfileName.asGiven("Signal"))
  setMuted(releaseChannelId, Long.MAX_VALUE)
  return releaseChannelId
}
