/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("StorageRecordProtoUtil")

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.AccountRecord

/**
 * Provide helpers for various Storage Service protos.
 */
object StorageRecordProtoUtil {

  /** Must match tag value specified for ManifestRecord.Identifier#type in StorageService.proto */
  const val STORAGE_ID_TYPE_TAG = 2

  @JvmStatic
  val defaultAccountRecord by lazy { AccountRecord() }
}
