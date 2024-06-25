/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.requests.CallLinkIncomingRequestSheet
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.toLink
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.Util

class UsernameLinkShareBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "link_share_bottom_sheet"
    const val KEY_COPY = "copy"

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      CallLinkIncomingRequestSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    Content(
      usernameLink = SignalStore.account.usernameLink?.toLink() ?: "",
      dismissDialog = { didCopy ->
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_COPY to didCopy))
        dismiss()
      }
    )
  }
}

@Composable
private fun Content(
  usernameLink: String,
  dismissDialog: (Boolean) -> Unit = {}
) {
  val context = LocalContext.current

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    BottomSheets.Handle()

    Text(
      text = stringResource(R.string.UsernameLinkShareBottomSheet_title),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(horizontal = 41.dp, vertical = 24.dp)
    )
    Text(
      text = usernameLink,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          shape = RoundedCornerShape(12.dp)
        )
        .padding(all = 16.dp)
    )
    ButtonRow(
      icon = painterResource(R.drawable.symbol_copy_android_24),
      text = stringResource(R.string.UsernameLinkShareBottomSheet_copy_link),
      modifier = Modifier.padding(top = 12.dp),
      onClick = {
        Util.copyToClipboard(context, usernameLink)
        dismissDialog(true)
      }
    )
    ButtonRow(
      icon = painterResource(R.drawable.symbol_share_android_24),
      text = stringResource(R.string.UsernameLinkShareBottomSheet_share),
      modifier = Modifier.padding(bottom = 12.dp),
      onClick = {
        dismissDialog(false)

        val sendIntent: Intent = Intent().apply {
          action = Intent.ACTION_SEND
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, usernameLink)
        }

        context.startActivity(Intent.createChooser(sendIntent, null))
      }
    )
  }
}

@Composable
private fun ButtonRow(icon: Painter, text: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Icon(
      painter = icon,
      contentDescription = text,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = 24.dp, vertical = 16.dp)
    )
    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(vertical = 16.dp)
    )
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreview() {
  SignalTheme {
    Surface {
      Content(
        usernameLink = "https://signal.me#eufzLWmFFUYAOqnVJ4Zlt0KqXf87r59FC1hZ3r7WipjKvgzMBg7DBlY5DB5hQTjsw0"
      )
    }
  }
}

@Preview(name = "Light Theme", group = "button row", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "button row", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonRowPreview() {
  SignalTheme {
    Surface {
      ButtonRow(icon = painterResource(R.drawable.symbol_share_android_24), text = "Share")
    }
  }
}
