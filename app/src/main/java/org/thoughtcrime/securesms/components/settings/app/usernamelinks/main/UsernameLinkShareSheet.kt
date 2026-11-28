/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.signal.appsettings.usernamelinks.UsernameLinkShareSheetContent
import org.signal.appsettings.usernamelinks.UsernameLinkShareSheetEvent
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.signal.core.util.Util

/**
 * A sheet for copying or sharing [usernameLink]. Copying calls [onLinkCopied] so the screen beneath can confirm it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameLinkShareSheet(
  usernameLink: String,
  onLinkCopied: () -> Unit,
  onDismissRequest: () -> Unit
) {
  val context = LocalContext.current

  BottomSheets.BottomSheet(onDismissRequest = onDismissRequest) {
    val sheet = LocalBottomSheetHostState.current

    UsernameLinkShareSheetContent(
      usernameLink = usernameLink,
      onEvent = { event ->
        sheet.dismiss()
        when (event) {
          UsernameLinkShareSheetEvent.CopyClicked -> {
            Util.copyToClipboard(context, usernameLink)
            onLinkCopied()
          }

          UsernameLinkShareSheetEvent.ShareClicked -> {
            val sendIntent = Intent(Intent.ACTION_SEND)
              .setType("text/plain")
              .putExtra(Intent.EXTRA_TEXT, usernameLink)

            context.startActivity(Intent.createChooser(sendIntent, null))
          }
        }
      }
    )
  }
}
