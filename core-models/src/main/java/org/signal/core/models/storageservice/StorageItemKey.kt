/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.storageservice

/**
 * Key used to encrypt individual storage items in the storage service.
 *
 * Created via [StorageKey.deriveItemKey].
 */
class StorageItemKey(val key: ByteArray) : StorageCipherKey {
  init {
    check(key.size == 32)
  }

  override fun serialize(): ByteArray = key.clone()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StorageItemKey

    return key.contentEquals(other.key)
  }

  override fun hashCode(): Int {
    return key.contentHashCode()
  }
}
