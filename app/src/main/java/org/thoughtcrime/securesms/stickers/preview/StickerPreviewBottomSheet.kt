/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.viewModel
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerManifest
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.signal.core.ui.R as CoreUiR

/**
 * Bottom sheet for a single sticker with the option to send and view pack when applicable
 */
class StickerPreviewBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_STICKER_LOCATOR = "arg.sticker.locator"
    private const val ARG_STICKER_URI = "arg.sticker.uri"
    private const val ARG_CONTENT_TYPE = "arg.content.type"

    @JvmStatic
    fun show(
      fragmentManager: FragmentManager,
      stickerLocator: StickerLocator,
      stickerUri: Uri?,
      contentType: String?
    ) {
      StickerPreviewBottomSheet().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_STICKER_LOCATOR, stickerLocator)
          putParcelable(ARG_STICKER_URI, stickerUri)
          putString(ARG_CONTENT_TYPE, contentType)
        }
        BottomSheetUtil.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG, this)
      }
    }
  }

  private val sticker: StickerManifest.Sticker by lazy {
    val locator = requireArguments().getParcelableCompat(ARG_STICKER_LOCATOR, StickerLocator::class.java)!!
    StickerManifest.Sticker(locator.packId, locator.packKey, locator.stickerId, locator.emoji, requireArguments().getString(ARG_CONTENT_TYPE), requireArguments().getParcelableCompat(ARG_STICKER_URI, Uri::class.java))
  }

  private val viewModel: StickerPreviewViewModel by viewModel {
    StickerPreviewViewModel(sticker.packId, sticker.packKey, sticker.id)
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions, ::handleAction)

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      BottomSheets.Handle()

      StickerPreviewSheetContent(
        sticker = sticker,
        stickerManifest = state.stickerManifest,
        canForward = true,
        onForwardClick = {
          openStickerShareSheet(sticker)
          dismissAllowingStateLoss()
        },
        onViewPackClick = {
          openPack(sticker)
          dismissAllowingStateLoss()
        },
        isFavorite = state.isFavorite.takeIf { RemoteConfig.internalUser },
        onFavoriteClick = { viewModel.onEvent(StickerPreviewEvent.FavoriteClicked) }
      )
    }

    if (state.showRemoveFavoriteDialog) {
      Dialogs.SimpleAlertDialog(
        title = stringResource(CoreUiR.string.StickerFavorites__remove_from_favorites_question),
        body = stringResource(CoreUiR.string.StickerFavorites__you_cant_send_it_or_add_it_to_favorites_again),
        confirm = stringResource(CoreUiR.string.StickerFavorites__remove),
        confirmColor = MaterialTheme.colorScheme.error,
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { viewModel.onEvent(StickerPreviewEvent.RemoveFavoriteConfirmed) },
        onDeny = { viewModel.onEvent(StickerPreviewEvent.RemoveFavoriteCanceled) },
        onDismissRequest = { viewModel.onEvent(StickerPreviewEvent.RemoveFavoriteCanceled) }
      )
    }
  }

  private fun handleAction(action: StickerPreviewAction) {
    when (action) {
      StickerPreviewAction.AddedToFavorites -> showSnackbar(getString(R.string.StickerFavorites__added_to_favorites))
      StickerPreviewAction.RemovedFromFavorites -> showSnackbar(getString(R.string.StickerFavorites__removed_from_favorites))
      StickerPreviewAction.FavoritesLimitReached -> showSnackbar(getString(R.string.StickerFavorites__favorites_limit_reached, StickerTables.MAX_FAVORITES))
    }
  }

  private fun showSnackbar(message: String) {
    parentFragment?.view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    dismissAllowingStateLoss()
  }

  private fun openStickerShareSheet(sticker: StickerManifest.Sticker) {
    val uri = sticker.uri.orNull() ?: return

    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = parentFragmentManager,
      multiselectForwardFragmentArgs = MultiselectForwardFragmentArgs(
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDataUri(uri)
            .withDataType(sticker.contentType?.takeIf { it.isNotBlank() } ?: MediaUtil.IMAGE_WEBP)
            .withStickerLocator(StickerLocator(sticker.packId, sticker.packKey, sticker.id, sticker.emoji))
            .build()
        ),
        title = R.string.StickerManagement_share_sheet_title
      )
    )
  }

  private fun openPack(sticker: StickerManifest.Sticker) {
    startActivity(StickerPackPreviewActivityV2.createIntent(StickerPackId(sticker.packId), StickerPackKey(sticker.packKey)))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPreviewSheetContent(
  stickerManifest: StickerManifest?,
  sticker: StickerManifest.Sticker,
  canForward: Boolean,
  onForwardClick: () -> Unit,
  onViewPackClick: (() -> Unit)? = null,
  isFavorite: Boolean? = null,
  onFavoriteClick: () -> Unit = {}
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

    val packDetails = if (stickerManifest != null) {
      val title = stickerManifest.title.orNull() ?: stringResource(R.string.StickerPackPreviewActivity_untitled)
      val author = stickerManifest.author.orNull() ?: stringResource(R.string.StickerManagement_author_unknown)
      stringResource(R.string.StickerPreviewBottomSheet__title_author, title, author)
    } else {
      ""
    }

    Text(
      text = packDetails,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    val showForward = canForward && sticker.uri.isPresent
    if (showForward || onViewPackClick != null || isFavorite != null) {
      Dividers.Default()
    }

    if (showForward) {
      Rows.TextRow(
        text = stringResource(R.string.StickerManagement_menu_send_pack),
        icon = SignalIcons.Forward.imageVector,
        onClick = onForwardClick
      )
    }

    if (isFavorite != null) {
      Rows.TextRow(
        text = stringResource(if (isFavorite) CoreUiR.string.StickerFavorites__remove_from_favorites else CoreUiR.string.StickerFavorites__add_to_favorites),
        icon = if (isFavorite) SignalIcons.FavoriteOff.imageVector else SignalIcons.Favorite.imageVector,
        onClick = onFavoriteClick
      )
    }

    if (onViewPackClick != null) {
      Rows.TextRow(
        text = stringResource(R.string.StickerPreviewBottomSheet__view_pack),
        icon = SignalIcons.StickerPack.imageVector,
        onClick = onViewPackClick
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
      sticker = cover,
      stickerManifest = StickerManifest(
        cover.packId,
        cover.packKey,
        "Misbrands".toOptional(),
        "Sticker Pack Author".toOptional(),
        cover.toOptional(),
        StickerPreviewDataFactory.manifestStickers(33)
      ),
      canForward = true,
      onForwardClick = {},
      onViewPackClick = {},
      isFavorite = false,
      onFavoriteClick = {}
    )
  }
}
