/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import okio.ByteString.Companion.toByteString
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord
import java.io.IOException

/**
 * A record in storage service that represents a call link that was already created.
 */
class SignalCallLinkRecord(
  override val id: StorageId,
  override val proto: CallLinkRecord
) : SignalRecord<CallLinkRecord> {

  val rootKey: ByteArray = proto.rootKey.toByteArray()
  val adminPassKey: ByteArray = proto.adminPasskey.toByteArray()
  val deletionTimestamp: Long = proto.deletedAtTimestampMs

  fun isDeleted(): Boolean {
    return deletionTimestamp > 0
  }

  class Builder(rawId: ByteArray, serializedUnknowns: ByteArray?) {
    private var id: StorageId = StorageId.forCallLink(rawId)
    private var builder: CallLinkRecord.Builder

    init {
      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns)
      } else {
        this.builder = CallLinkRecord.Builder()
      }
    }

    fun setRootKey(rootKey: ByteArray): Builder {
      builder.rootKey = rootKey.toByteString()
      return this
    }

    fun setAdminPassKey(adminPasskey: ByteArray): Builder {
      builder.adminPasskey = adminPasskey.toByteString()
      return this
    }

    fun setDeletedTimestamp(deletedTimestamp: Long): Builder {
      builder.deletedAtTimestampMs = deletedTimestamp
      return this
    }

    fun build(): SignalCallLinkRecord {
      return SignalCallLinkRecord(id, builder.build())
    }

    companion object {
      fun parseUnknowns(serializedUnknowns: ByteArray): CallLinkRecord.Builder {
        return try {
          CallLinkRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
        } catch (e: IOException) {
          CallLinkRecord.Builder()
        }
      }
    }
  }
}
