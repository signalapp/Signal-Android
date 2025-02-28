/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import okio.IOException
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBlob
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.backup.v2.proto.GroupInvitationDeclinedUpdate
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Ensure we store ACIs only in the ACI only-field and null for PNIs for field [GroupInvitationDeclinedUpdate.inviteeAci] in [GroupInvitationDeclinedUpdate].
 */
@Suppress("ClassName")
object V267_FixGroupInvitationDeclinedUpdate : SignalDatabaseMigration {

  private val TAG = Log.tag(V267_FixGroupInvitationDeclinedUpdate::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val messageExtrasFixes = mutableListOf<Pair<Long, ByteArray>>()

    db.query("message", arrayOf("_id", "message_extras"), "message_extras IS NOT NULL AND type & 0x10000 != 0", null, null, null, null)
      .forEach { cursor ->
        val blob = cursor.requireBlob("message_extras")!!

        val messageExtras: MessageExtras? = try {
          MessageExtras.ADAPTER.decode(blob)
        } catch (e: IOException) {
          Log.w(TAG, "Unable to decode message extras", e)
          null
        }

        if (messageExtras?.gv2UpdateDescription?.groupChangeUpdate?.updates?.any { it.groupInvitationDeclinedUpdate != null } != true) {
          return@forEach
        }

        val groupUpdateDescription = messageExtras.gv2UpdateDescription
        val groupUpdate = groupUpdateDescription.groupChangeUpdate!!
        val updates = groupUpdate.updates.toMutableList()

        updates
          .replaceAll { change ->
            if (change.groupInvitationDeclinedUpdate != null && ServiceId.parseOrNull(change.groupInvitationDeclinedUpdate.inviteeAci) is ServiceId.PNI) {
              change.copy(groupInvitationDeclinedUpdate = change.groupInvitationDeclinedUpdate.copy(inviteeAci = null))
            } else {
              change
            }
          }

        val updatedMessageExtras = messageExtras.copy(
          gv2UpdateDescription = groupUpdateDescription.copy(
            groupChangeUpdate = groupUpdate.copy(
              updates = updates
            )
          )
        )

        messageExtrasFixes += cursor.requireLong("_id") to updatedMessageExtras.encode()
      }

    messageExtrasFixes.forEach { (id, extras) ->
      db.update("message", contentValuesOf("message_extras" to extras), "_id = $id", null)
    }
  }
}
