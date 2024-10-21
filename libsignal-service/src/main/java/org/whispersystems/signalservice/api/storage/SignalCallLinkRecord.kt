/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import okio.ByteString.Companion.toByteString
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord
import java.io.IOException
import java.util.LinkedList

/**
 * A record in storage service that represents a call link that was already created.
 */
class SignalCallLinkRecord(private val id: StorageId, private val proto: CallLinkRecord) : SignalRecord {

  val rootKey: ByteArray = proto.rootKey.toByteArray()
  val adminPassKey: ByteArray = proto.adminPasskey.toByteArray()
  val deletionTimestamp: Long = proto.deletedAtTimestampMs

  fun toProto(): CallLinkRecord {
    return proto
  }

  override fun getId(): StorageId {
    return id
  }

  override fun asStorageRecord(): SignalStorageRecord {
    return SignalStorageRecord.forCallLink(this)
  }

  override fun describeDiff(other: SignalRecord?): String {
    return when (other) {
      is SignalCallLinkRecord -> {
        val diff = LinkedList<String>()
        if (!rootKey.contentEquals(other.rootKey)) {
          diff.add("RootKey")
        }

        if (!adminPassKey.contentEquals(other.adminPassKey)) {
          diff.add("AdminPassKey")
        }

        if (deletionTimestamp != other.deletionTimestamp) {
          diff.add("DeletionTimestamp")
        }

        diff.toString()
      }

      null -> {
        "Other was null!"
      }

      else -> {
        "Different class. ${this::class.java.getSimpleName()} | ${other::class.java.getSimpleName()}"
      }
    }
  }

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
