/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.whispersystems.signalservice.api.storage.SignalCallLinkRecord
import java.util.Optional

internal class CallLinkRecordProcessor : DefaultStorageRecordProcessor<SignalCallLinkRecord>() {

  companion object {
    private val TAG = Log.tag(CallLinkRecordProcessor::class)
  }

  override fun compare(o1: SignalCallLinkRecord?, o2: SignalCallLinkRecord?): Int {
    return if (o1?.rootKey.contentEquals(o2?.rootKey)) {
      0
    } else {
      1
    }
  }

  internal override fun isInvalid(remote: SignalCallLinkRecord): Boolean {
    return remote.adminPassKey.isNotEmpty() && remote.deletionTimestamp > 0L
  }

  internal override fun getMatching(remote: SignalCallLinkRecord, keyGenerator: StorageKeyGenerator): Optional<SignalCallLinkRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val rootKey = CallLinkRootKey(remote.rootKey)
    val roomId = CallLinkRoomId.fromCallLinkRootKey(rootKey)
    val callLink = SignalDatabase.callLinks.getCallLinkByRoomId(roomId)
    if (callLink != null && callLink.credentials?.adminPassBytes != null) {
      val builder = SignalCallLinkRecord.Builder(keyGenerator.generate(), null).apply {
        setRootKey(rootKey.keyBytes)
        setAdminPassKey(callLink.credentials.adminPassBytes)
        setDeletedTimestamp(callLink.deletionTimestamp)
      }
      return Optional.of(builder.build())
    } else {
      return Optional.empty<SignalCallLinkRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * An earlier deletion takes precedence over a later deletion
   * Other fields should not change, except for the clearing of the admin passkey on deletion
   */
  internal override fun merge(remote: SignalCallLinkRecord, local: SignalCallLinkRecord, keyGenerator: StorageKeyGenerator): SignalCallLinkRecord {
    return if (remote.isDeleted() && local.isDeleted()) {
      if (remote.deletionTimestamp < local.deletionTimestamp) {
        remote
      } else {
        local
      }
    } else if (remote.isDeleted()) {
      remote
    } else if (local.isDeleted()) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: SignalCallLinkRecord) {
    insertOrUpdateRecord(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalCallLinkRecord>) {
    insertOrUpdateRecord(update.new)
  }

  private fun insertOrUpdateRecord(record: SignalCallLinkRecord) {
    val rootKey = CallLinkRootKey(record.rootKey)

    SignalDatabase.callLinks.insertOrUpdateCallLinkByRootKey(
      callLinkRootKey = rootKey,
      adminPassKey = record.adminPassKey,
      deletionTimestamp = record.deletionTimestamp,
      storageId = record.id
    )
  }
}
