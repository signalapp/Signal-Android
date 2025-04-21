/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.backup.v2.ArchiveRecipient
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.getAllForBackup
import org.thoughtcrime.securesms.backup.v2.database.getCallLinksForBackup
import org.thoughtcrime.securesms.backup.v2.database.getContactsForBackup
import org.thoughtcrime.securesms.backup.v2.database.getGroupsForBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreReleaseNotes
import org.thoughtcrime.securesms.backup.v2.importer.CallLinkArchiveImporter
import org.thoughtcrime.securesms.backup.v2.importer.ContactArchiveImporter
import org.thoughtcrime.securesms.backup.v2.importer.DistributionListArchiveImporter
import org.thoughtcrime.securesms.backup.v2.importer.GroupArchiveImporter
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.ReleaseNotes
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.backup.v2.util.toLocal
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Handles importing/exporting [ArchiveRecipient] frames for an archive.
 */
object RecipientArchiveProcessor {

  val TAG = Log.tag(RecipientArchiveProcessor::class.java)

  fun export(db: SignalDatabase, signalStore: SignalStore, exportState: ExportState, selfRecipientId: RecipientId, selfAci: ServiceId.ACI, emitter: BackupFrameEmitter) {
    exportState.recipientIds.add(selfRecipientId.toLong())
    exportState.contactRecipientIds.add(selfRecipientId.toLong())
    exportState.recipientIdToAci[selfRecipientId.toLong()] = selfAci.toByteString()
    exportState.aciToRecipientId[selfAci.toString()] = selfRecipientId.toLong()

    val releaseChannelId = signalStore.releaseChannelValues.releaseChannelRecipientId
    if (releaseChannelId != null) {
      exportState.recipientIds.add(releaseChannelId.toLong())
      exportState.contactRecipientIds.add(releaseChannelId.toLong())
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

    db.recipientTable.getContactsForBackup(selfRecipientId.toLong()).use { reader ->
      for (recipient in reader) {
        if (recipient != null) {
          exportState.recipientIds.add(recipient.id)
          exportState.contactRecipientIds.add(recipient.id)
          recipient.contact?.aci?.let {
            exportState.recipientIdToAci[recipient.id] = it
            exportState.aciToRecipientId[ServiceId.ACI.parseOrThrow(it).toString()] = recipient.id
          }

          emitter.emit(Frame(recipient = recipient))
        }
      }
    }

    db.recipientTable.getGroupsForBackup(selfAci).use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        exportState.groupRecipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.distributionListTables.getAllForBackup(selfRecipientId).use { reader ->
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
      recipient.contact != null -> ContactArchiveImporter.import(recipient.contact)
      recipient.group != null -> GroupArchiveImporter.import(recipient.group)
      recipient.distributionList != null -> DistributionListArchiveImporter.import(recipient.distributionList, importState)
      recipient.releaseNotes != null -> SignalDatabase.recipients.restoreReleaseNotes()
      recipient.callLink != null -> CallLinkArchiveImporter.import(recipient.callLink)
      recipient.self != null -> {
        SignalDatabase.writableDatabase
          .update(RecipientTable.TABLE_NAME)
          .values(RecipientTable.AVATAR_COLOR to recipient.self.avatarColor?.toLocal()?.serialize())
          .where("${RecipientTable.ID} = ?", Recipient.self().id)
          .run()
        Recipient.self().id
      }
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
