/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object UriUtil {

  /**
   * Ensures that an external URI is valid and doesn't contain any references to internal files or
   * any other trickiness. Rejects file:// URIs that reference internal storage, and content:// URIs
   * whose authority belongs to this application's own content providers.
   */
  @JvmStatic
  fun isValidExternalUri(context: Context, uri: Uri): Boolean {
    if (ContentResolver.SCHEME_FILE == uri.scheme) {
      try {
        val file = File(uri.path)

        return file.canonicalPath == file.path &&
          !file.canonicalPath.startsWith("/data") &&
          !file.canonicalPath.contains(context.packageName)
      } catch (e: IOException) {
        return false
      }
    } else if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
      val authority = uri.authority ?: return false
      return !authority.startsWith(context.packageName)
    } else {
      return true
    }
  }

  /**
   * Parses a string to a URI if it's valid, otherwise null.
   */
  fun parseOrNull(uri: String): Uri? {
    return try {
      Uri.parse(uri)
    } catch (e: Exception) {
      null
    }
  }
}
