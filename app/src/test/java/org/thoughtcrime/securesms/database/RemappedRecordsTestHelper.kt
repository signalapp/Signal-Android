/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

/**
 * Bridge to package-private [RemappedRecords] internals for use from test rules.
 */
object RemappedRecordsTestHelper {
  fun resetInstance() {
    RemappedRecords.getInstance().resetCache()
  }
}
