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
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Fetches all individual contacts for backups and returns the result as an iterator.
 * It's important to note that the iterator still needs to be closed after it's used.
 * It's recommended to use `.use` or a try-with-resources pattern.
 */
fun RecipientTable.getContactsForBackup(selfId: Long): ContactArchiveExporter {
  val cursor = readableDatabase
    .select(
      "${RecipientTable.TABLE_NAME}.${RecipientTable.ID}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.ACI_COLUMN}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PNI_COLUMN}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.USERNAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.E164}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.HIDDEN}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.REGISTERED}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.UNREGISTERED_TIMESTAMP}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_KEY}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_SHARING}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_GIVEN_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_FAMILY_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_JOINED_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_GIVEN_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_FAMILY_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_NICKNAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.NICKNAME_GIVEN_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.NICKNAME_FAMILY_NAME}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.NOTE}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.CHAT_COLORS}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.CUSTOM_CHAT_COLORS_ID}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.AVATAR_COLOR}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.EXTRAS}",
      "${IdentityTable.TABLE_NAME}.${IdentityTable.IDENTITY_KEY}",
      "${IdentityTable.TABLE_NAME}.${IdentityTable.VERIFIED}"
    )
    .from(
      """
      ${RecipientTable.TABLE_NAME} LEFT OUTER JOIN ${IdentityTable.TABLE_NAME} ON (
        ${RecipientTable.TABLE_NAME}.${RecipientTable.ACI_COLUMN} = ${IdentityTable.TABLE_NAME}.${IdentityTable.ADDRESS} OR (
          ${RecipientTable.TABLE_NAME}.${RecipientTable.ACI_COLUMN} IS NULL AND ${RecipientTable.TABLE_NAME}.${RecipientTable.PNI_COLUMN} = ${IdentityTable.TABLE_NAME}.${IdentityTable.ADDRESS}
        ) 
      )
      """
    )
    .where(
      """
      ${RecipientTable.TYPE} = ? AND (
        ${RecipientTable.ACI_COLUMN} NOT NULL OR
        (${RecipientTable.PNI_COLUMN} NOT NULL AND ${RecipientTable.E164} NOT NULL AND ${RecipientTable.E164} != 0) OR
        (${RecipientTable.E164} NOT NULL AND ${RecipientTable.E164} != 0)
      )
      """,
      RecipientTable.RecipientType.INDIVIDUAL.id
    )
    .run()

  return ContactArchiveExporter(cursor, selfId)
}

fun RecipientTable.getGroupsForBackup(selfAci: ServiceId.ACI): GroupArchiveExporter {
  val cursor = readableDatabase
    .select(
      "${RecipientTable.TABLE_NAME}.${RecipientTable.ID}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_SHARING}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.EXTRAS}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.AVATAR_COLOR}",
      "${GroupTable.TABLE_NAME}.${GroupTable.V2_MASTER_KEY}",
      "${GroupTable.TABLE_NAME}.${GroupTable.SHOW_AS_STORY_STATE}",
      "${GroupTable.TABLE_NAME}.${GroupTable.TITLE}",
      "${GroupTable.TABLE_NAME}.${GroupTable.ACTIVE}",
      "${GroupTable.TABLE_NAME}.${GroupTable.V2_DECRYPTED_GROUP}"
    )
    .from(
      """
      ${RecipientTable.TABLE_NAME} 
        INNER JOIN ${GroupTable.TABLE_NAME} ON ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} = ${GroupTable.TABLE_NAME}.${GroupTable.RECIPIENT_ID}
      """
    )
    .where(
      """
      ${GroupTable.TABLE_NAME}.${GroupTable.V2_MASTER_KEY} IS NOT NULL AND
      ${GroupTable.TABLE_NAME}.${GroupTable.V2_REVISION} >= 0
      """
    )
    .run()

  return GroupArchiveExporter(selfAci, cursor)
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
