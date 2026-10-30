/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

class MockKeyValuePersistentStorage private constructor(private val dataSet: KeyValueDataSet) : KeyValuePersistentStorage {

  override fun writeDataSet(dataSet: KeyValueDataSet, removes: Collection<String>) {
    this.dataSet.putAll(dataSet)
    this.dataSet.removeAll(removes)
  }

  override fun getDataSet(): KeyValueDataSet {
    return dataSet
  }

  companion object {
    @JvmStatic
    fun withDataSet(dataSet: KeyValueDataSet): KeyValuePersistentStorage {
      return MockKeyValuePersistentStorage(dataSet)
    }
  }
}
