/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import org.signal.core.ui.contracts.OpenDocumentContract
import org.signal.core.ui.contracts.OpenDocumentTreeContract

object Launchers {

  /**
   * Returns a launcher for ACTION_OPEN_DOCUMENT_TREE that invokes [onResult] with the selected
   * Uri, or null if the user cancels.
   */
  @Composable
  fun rememberOpenDocumentTreeLauncher(
    onResult: (Uri?) -> Unit
  ): ActivityResultLauncher<Uri?> {
    return rememberLauncherForActivityResult(OpenDocumentTreeContract()) { uri ->
      onResult(uri)
    }
  }

  /**
   * Returns a launcher for ACTION_OPEN_DOCUMENT / ACTION_GET_CONTENT that invokes [onResult]
   * with the selected Uri, or null if the user cancels.
   */
  @Composable
  @Suppress("unused")
  fun rememberOpenDocumentLauncher(
    onResult: (Uri?) -> Unit
  ): ActivityResultLauncher<OpenDocumentContract.Input> {
    return rememberLauncherForActivityResult(OpenDocumentContract()) { uri ->
      onResult(uri)
    }
  }
}
