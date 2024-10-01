/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ArchiveRecipient
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.getAllForBackup
import org.thoughtcrime.securesms.backup.v2.database.getCallLinksForBackup
import org.thoughtcrime.securesms.backup.v2.database.getContactsForBackup
import org.thoughtcrime.securesms.backup.v2.database.getGroupsForBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreContactFromBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreFromBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreGroupFromBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreReleaseNotes
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.ReleaseNotes
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Handles importing/exporting [ArchiveRecipient] frames for an archive.
 */
object RecipientBackupProcessor {

  val TAG = Log.tag(RecipientBackupProcessor::class.java)

  fun export(db: SignalDatabase, signalStore: SignalStore, exportState: ExportState, emitter: BackupFrameEmitter) {
    val selfId = db.recipientTable.getByAci(signalStore.accountValues.aci!!).get().toLong()
    val releaseChannelId = signalStore.releaseChannelValues.releaseChannelRecipientId
    if (releaseChannelId != null) {
      exportState.recipientIds.add(releaseChannelId.toLong())
      emitter.emit(
        Frame(
          recipient = ArchiveRecipient(
            id = releaseChannelId.toLong(),
            releaseNotes = ReleaseNotes()
          )
        )
      )
    } else {
      Log.w(TAG, "Missing release channel id on export!")
    }

    db.recipientTable.getContactsForBackup(selfId).use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.recipientTable.getGroupsForBackup().use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.distributionListTables.getAllForBackup().use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.callLinkTable.getCallLinksForBackup().use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }
  }

  fun import(recipient: ArchiveRecipient, importState: ImportState) {
    val newId = when {
      recipient.contact != null -> SignalDatabase.recipients.restoreContactFromBackup(recipient.contact)
      recipient.group != null -> SignalDatabase.recipients.restoreGroupFromBackup(recipient.group)
      recipient.distributionList != null -> SignalDatabase.distributionLists.restoreFromBackup(recipient.distributionList, importState)
      recipient.self != null -> Recipient.self().id
      recipient.releaseNotes != null -> SignalDatabase.recipients.restoreReleaseNotes()
      recipient.callLink != null -> SignalDatabase.callLinks.restoreFromBackup(recipient.callLink)
      else -> {
        Log.w(TAG, "Unrecognized recipient type!")
        null
      }
    }
    if (newId != null) {
      importState.remoteToLocalRecipientId[recipient.id] = newId
    }
  }
}
