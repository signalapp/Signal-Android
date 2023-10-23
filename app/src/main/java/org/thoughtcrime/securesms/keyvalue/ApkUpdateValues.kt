/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

internal class ApkUpdateValues(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private const val DOWNLOAD_ID = "apk_update.download_id"
    private const val DIGEST = "apk_update.digest"
    private const val AUTO_UPDATE = "apk_update.auto_update"
  }

  override fun onFirstEverAppLaunch() = Unit
  override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  val downloadId: Long by longValue(DOWNLOAD_ID, -2)
  val digest: ByteArray? get() = store.getBlob(DIGEST, null)
  val autoUpdate: Boolean by booleanValue(AUTO_UPDATE, true)

  fun setDownloadAttributes(id: Long, digest: ByteArray?) {
    store
      .beginWrite()
      .putLong(DOWNLOAD_ID, id)
      .putBlob(DIGEST, digest)
      .commit()
  }

  fun clearDownloadAttributes() {
    store
      .beginWrite()
      .putLong(DOWNLOAD_ID, -1)
      .putBlob(DIGEST, null)
      .commit()
  }
}
