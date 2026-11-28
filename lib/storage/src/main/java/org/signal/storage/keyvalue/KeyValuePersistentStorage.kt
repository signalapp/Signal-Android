/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

interface KeyValuePersistentStorage {
  fun writeDataSet(dataSet: KeyValueDataSet, removes: Collection<String>)
  fun getDataSet(): KeyValueDataSet
}
