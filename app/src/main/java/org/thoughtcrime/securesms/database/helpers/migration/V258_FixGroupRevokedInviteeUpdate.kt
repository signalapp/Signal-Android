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
import org.thoughtcrime.securesms.backup.v2.proto.GroupInvitationRevokedUpdate
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Ensure we store ACIs only in the ACI fields and non-byte prefixed PNIs only in the PNI fields of
 * [GroupInvitationRevokedUpdate.Invitee] in [GroupInvitationRevokedUpdate].
 */
@Suppress("ClassName")
object V258_FixGroupRevokedInviteeUpdate : SignalDatabaseMigration {

  private val TAG = Log.tag(V258_FixGroupRevokedInviteeUpdate::class)

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

        if (messageExtras?.gv2UpdateDescription?.groupChangeUpdate?.updates?.any { it.groupInvitationRevokedUpdate != null } != true) {
          return@forEach
        }

        val groupUpdateDescription = messageExtras.gv2UpdateDescription
        val groupUpdate = groupUpdateDescription.groupChangeUpdate!!
        val updates = groupUpdate.updates.toMutableList()

        updates
          .replaceAll { change ->
            if (change.groupInvitationRevokedUpdate != null) {
              val invitees = change.groupInvitationRevokedUpdate.invitees.toMutableList()

              invitees.replaceAll { invitee ->
                val inviteeAciFieldServiceId = ServiceId.parseOrNull(invitee.inviteeAci)
                val inviteePniFieldServiceId = ServiceId.parseOrNull(invitee.inviteePni)

                if (inviteeAciFieldServiceId is ServiceId.PNI) {
                  // We have an obvious PNI in the ACI field, move to PNI field without byte prefix
                  invitee.copy(inviteeAci = null, inviteePni = inviteeAciFieldServiceId.toByteStringWithoutPrefix())
                } else if (inviteePniFieldServiceId is ServiceId.PNI) {
                  // We have a byte-prefixed encoded PNI in the PNI field, update to remove byte prefix
                  invitee.copy(inviteePni = inviteePniFieldServiceId.toByteStringWithoutPrefix())
                } else {
                  // ACI field doesn't have an obvious PNI and PNI doesn't have a byte-prefixed PNI, no fix needed
                  invitee
                }
              }

              change.copy(groupInvitationRevokedUpdate = change.groupInvitationRevokedUpdate.copy(invitees = invitees))
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
