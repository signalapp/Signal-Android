/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.api.crypto.Crypto
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageItem
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.util.StringUtil

/**
 * A wrapper around a [ByteArray], just so the recordIkm is strongly typed.
 * The recordIkm comes from [ManifestRecord.recordIkm], and is used to encrypt [StorageItem.value_].
 */
@JvmInline
value class RecordIkm(val value: ByteArray) {

  companion object {
    fun generate(): RecordIkm {
      return RecordIkm(Util.getSecretBytes(32))
    }
  }

  fun deriveStorageItemKey(rawId: ByteArray): StorageItemKey {
    val key = Crypto.hkdf(
      inputKeyMaterial = this.value,
      info = StringUtil.utf8("20240801_SIGNAL_STORAGE_SERVICE_ITEM_") + rawId,
      outputLength = 32
    )

    return StorageItemKey(key)
  }
}
