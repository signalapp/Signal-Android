/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stickers.StickerManifest
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPreviewSheetContent(
  stickerManifest: StickerManifest,
  sticker: StickerManifest.Sticker,
  isPackInstalled: Boolean,
  onForwardClick: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    StickerImage(
      sticker = sticker,
      modifier = Modifier
        .padding(vertical = 24.dp)
        .size(144.dp)
    )

    val title = stickerManifest.title.orNull() ?: stringResource(R.string.StickerPackPreviewActivity_untitled)
    val author = stickerManifest.author.orNull() ?: stringResource(R.string.StickerManagement_author_unknown)

    Text(
      text = stringResource(R.string.StickerPreviewBottomSheet__title_author, title, author),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    if (isPackInstalled && sticker.uri.isPresent) {
      Dividers.Default()

      Rows.TextRow(
        text = stringResource(R.string.StickerManagement_menu_send_pack),
        icon = SignalIcons.Forward.imageVector,
        onClick = onForwardClick
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun StickerPackShareSheetContentPreview() {
  Previews.BottomSheetContentPreview {
    val cover = StickerManifest.Sticker("packId0", "packKey0", 0, "👍", null, Uri.EMPTY)

    StickerPreviewSheetContent(
      stickerManifest = StickerManifest(
        cover.packId,
        cover.packKey,
        "Misbrands".toOptional(),
        "Sticker Pack Author".toOptional(),
        cover.toOptional(),
        StickerPreviewDataFactory.manifestStickers(33)
      ),
      sticker = cover,
      isPackInstalled = true,
      onForwardClick = {}
    )
  }
}
