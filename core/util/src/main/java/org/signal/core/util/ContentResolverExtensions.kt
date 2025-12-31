/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okio.IOException

@Throws(IOException::class)
fun ContentResolver.getLength(uri: Uri): Long? {
  return this.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      cursor.requireLongOrNull(OpenableColumns.SIZE)
    } else {
      null
    }
  } ?: openInputStream(uri)?.use { it.readLength() }
}
