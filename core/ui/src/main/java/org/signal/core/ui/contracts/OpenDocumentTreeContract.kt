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
 * ActivityResultContract for selecting a folder via ACTION_OPEN_DOCUMENT_TREE.
 *
 * The returned Uri can be persisted with takePersistableUriPermission for long-term access.
 */
class OpenDocumentTreeContract : ActivityResultContract<Uri?, Uri?>() {

  override fun createIntent(context: Context, input: Uri?): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      if (Build.VERSION.SDK_INT >= 26 && input != null) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
      }

      addFlags(
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
          Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
    }
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    return if (resultCode == Activity.RESULT_OK) intent?.data else null
  }
}
