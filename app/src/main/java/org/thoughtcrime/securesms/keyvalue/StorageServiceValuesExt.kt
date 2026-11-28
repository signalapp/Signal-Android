/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.storage.keyvalue.StorageServiceValues
import org.whispersystems.signalservice.api.storage.SignalStorageManifest

var StorageServiceValues.manifest: SignalStorageManifest
  get() = manifestBytes?.let { SignalStorageManifest.deserialize(it) } ?: SignalStorageManifest.EMPTY
  set(value) {
    manifestBytes = value.serialize()
  }
