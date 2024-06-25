/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log

class ApkUpdateValues(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private val TAG = Log.tag(ApkUpdateValues::class.java)

    private const val DOWNLOAD_ID = "apk_update.download_id"
    private const val DIGEST = "apk_update.digest"
    private const val AUTO_UPDATE = "apk_update.auto_update"
    private const val LAST_SUCCESSFUL_CHECK = "apk_update.last_successful_check"
    private const val LAST_APK_UPLOAD_TIME = "apk_update.last_apk_upload_time"
    private const val PENDING_APK_UPLOAD_TIME = "apk_update.pending_apk_upload_time"
  }

  override fun onFirstEverAppLaunch() = Unit
  override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  val downloadId: Long by longValue(DOWNLOAD_ID, -2)
  val digest: ByteArray? get() = store.getBlob(DIGEST, null)
  var autoUpdate: Boolean by booleanValue(AUTO_UPDATE, true)
  var lastSuccessfulCheck: Long by longValue(LAST_SUCCESSFUL_CHECK, 0)

  /** The upload of the last APK we installed */
  var lastApkUploadTime: Long
    get() = getLong(LAST_APK_UPLOAD_TIME, 0)
    set(value) {
      Log.d(TAG, "Setting lastApkUploadTime to $value")
      store.beginWrite().putLong(LAST_APK_UPLOAD_TIME, value).commit()
    }

  /** The upload time of the APK we're trying to install */
  val pendingApkUploadTime: Long by longValue(PENDING_APK_UPLOAD_TIME, 0)

  fun setDownloadAttributes(id: Long, digest: ByteArray?, apkUploadTime: Long) {
    Log.d(TAG, "Saving download attributes. id: $id, apkUploadTime: $apkUploadTime")

    store
      .beginWrite()
      .putLong(DOWNLOAD_ID, id)
      .putBlob(DIGEST, digest)
      .putLong(PENDING_APK_UPLOAD_TIME, apkUploadTime)
      .commit()
  }

  fun clearDownloadAttributes() {
    Log.d(TAG, "Clearing download attributes.")

    store
      .beginWrite()
      .putLong(DOWNLOAD_ID, -1)
      .putBlob(DIGEST, null)
      .putLong(PENDING_APK_UPLOAD_TIME, 0)
      .commit()
  }
}
