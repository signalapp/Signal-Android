/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * ActivityResultContract for selecting a single file.
 *
 * Use [Mode.DOCUMENT] (ACTION_OPEN_DOCUMENT) when you need a Uri that can be persisted via
 * takePersistableUriPermission. Use [Mode.CONTENT] (ACTION_GET_CONTENT) for one-off access where
 * persistence is not needed.
 */
class OpenDocumentContract : ActivityResultContract<OpenDocumentContract.Input, Uri?>() {

  data class Input(
    val mode: Mode = Mode.DOCUMENT,
    val mimeTypes: List<String> = listOf("*/*"),
    val initialUri: Uri? = null
  )

  enum class Mode {
    DOCUMENT,
    CONTENT
  }

  override fun createIntent(context: Context, input: Input): Intent {
    return Intent().apply {
      action = when (input.mode) {
        Mode.DOCUMENT -> Intent.ACTION_OPEN_DOCUMENT
        Mode.CONTENT -> Intent.ACTION_GET_CONTENT
      }

      type = "*/*"

      if (input.mimeTypes.isNotEmpty()) {
        putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes.toTypedArray())
      }

      if (Build.VERSION.SDK_INT >= 26 && input.initialUri != null) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
      }

      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      if (input.mode == Mode.DOCUMENT) {
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
      }
    }
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    return if (resultCode == Activity.RESULT_OK) intent?.data else null
  }
}
