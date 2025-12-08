/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.storageservice

import org.signal.core.util.Base64
import org.signal.core.util.CryptoUtil

/**
 * Key used to encrypt data on the storage service. Not used directly -- instead we used keys that
 * are derived for each item we're storing.
 *
 * Created via [org.signal.core.models.MasterKey.deriveStorageServiceKey].
 */
class StorageKey(val key: ByteArray) {
  init {
    check(key.size == 32)
  }

  fun deriveManifestKey(version: Long): StorageManifestKey {
    return StorageManifestKey(derive("Manifest_$version"))
  }

  fun deriveItemKey(rawId: ByteArray): StorageItemKey {
    return StorageItemKey(derive("Item_" + Base64.encodeWithPadding(rawId)))
  }

  private fun derive(keyName: String): ByteArray {
    return CryptoUtil.hmacSha256(key, keyName.toByteArray(Charsets.UTF_8))
  }

  fun serialize(): ByteArray {
    return key.clone()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StorageKey

    return key.contentEquals(other.key)
  }

  override fun hashCode(): Int {
    return key.contentHashCode()
  }
}
