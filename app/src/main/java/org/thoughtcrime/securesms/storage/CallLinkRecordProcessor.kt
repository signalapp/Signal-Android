/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.signal.ringrtc.CallLinkEpoch
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.whispersystems.signalservice.api.storage.SignalCallLinkRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toSignalCallLinkRecord
import java.util.Optional

/**
 * Record processor for [SignalCallLinkRecord].
 * Handles merging and updating our local store when processing remote call link storage records.
 */
class CallLinkRecordProcessor : DefaultStorageRecordProcessor<SignalCallLinkRecord>() {

  companion object {
    private val TAG = Log.tag(CallLinkRecordProcessor::class)
  }

  override fun compare(o1: SignalCallLinkRecord?, o2: SignalCallLinkRecord?): Int {
    return if (o1?.proto?.rootKey == o2?.proto?.rootKey) {
      0
    } else {
      1
    }
  }

  override fun isInvalid(remote: SignalCallLinkRecord): Boolean {
    return remote.proto.adminPasskey.isNotEmpty() && remote.proto.deletedAtTimestampMs > 0L
  }

  override fun getMatching(remote: SignalCallLinkRecord, keyGenerator: StorageKeyGenerator): Optional<SignalCallLinkRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val callRootKey = CallLinkRootKey(remote.proto.rootKey.toByteArray())
    val roomId = CallLinkRoomId.fromCallLinkRootKey(callRootKey)
    val callLink = SignalDatabase.callLinks.getCallLinkByRoomId(roomId)

    if (callLink != null && callLink.credentials?.adminPassBytes != null) {
      val epochBytes = callLink.credentials.epochBytes
      return SignalCallLinkRecord.newBuilder(null).apply {
        rootKey = callRootKey.keyBytes.toByteString()
        epoch = epochBytes?.toByteString() ?: ByteString.EMPTY
        adminPasskey = callLink.credentials.adminPassBytes.toByteString()
        deletedAtTimestampMs = callLink.deletionTimestamp
      }.build().toSignalCallLinkRecord(StorageId.forCallLink(keyGenerator.generate())).toOptional()
    } else {
      return Optional.empty<SignalCallLinkRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * An earlier deletion takes precedence over a later deletion
   * Other fields should not change, except for the clearing of the admin passkey on deletion
   */
  override fun merge(remote: SignalCallLinkRecord, local: SignalCallLinkRecord, keyGenerator: StorageKeyGenerator): SignalCallLinkRecord {
    return if (remote.proto.deletedAtTimestampMs > 0 && local.proto.deletedAtTimestampMs > 0) {
      if (remote.proto.deletedAtTimestampMs < local.proto.deletedAtTimestampMs) {
        remote
      } else {
        local
      }
    } else if (remote.proto.deletedAtTimestampMs > 0) {
      remote
    } else if (local.proto.deletedAtTimestampMs > 0) {
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
    val rootKey = CallLinkRootKey(record.proto.rootKey.toByteArray())

    val epoch = if (record.proto.epoch.isNotEmpty()) {
      CallLinkEpoch.fromBytes(record.proto.epoch.toByteArray())
    } else {
      null
    }

    SignalDatabase.callLinks.insertOrUpdateCallLinkByRootKey(
      callLinkRootKey = rootKey,
      callLinkEpoch = epoch,
      adminPassKey = record.proto.adminPasskey.toByteArray(),
      deletionTimestamp = record.proto.deletedAtTimestampMs,
      storageId = record.id
    )
  }
}
