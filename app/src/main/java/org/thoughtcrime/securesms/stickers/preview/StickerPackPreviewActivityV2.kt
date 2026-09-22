/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.load.Key
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackParams
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerManifest
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory
import org.thoughtcrime.securesms.stickers.StickerRemoteUri
import org.thoughtcrime.securesms.stickers.StickerUrl
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewUiState.ContentState
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewUiState.UserPrompt
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.viewModel
import java.text.NumberFormat
import kotlin.jvm.optionals.getOrElse

/**
 * Shows the contents of a pack and allows the user to install it (if not installed) or remove it
 * (if installed). This is also the handler for sticker pack deep links.
 */
class StickerPackPreviewActivityV2 : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(
      packId: StickerPackId,
      packKey: StickerPackKey
    ): Intent {
      return Intent(Intent.ACTION_VIEW, StickerUrl.createActionUri(packId.value, packKey.value)).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
      }
    }
  }

  private val viewModel: StickerPackPreviewViewModelV2 by viewModel {
    StickerPackPreviewViewModelV2(
      params = StickerPackParams.fromExternalUri(intent.data)
    )
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      CollectActions(viewModel.actions, ::handleAction)

      SignalTheme {
        StickerPackPreviewScreen(
          uiState = uiState,
          onEvent = viewModel::onEvent,
          onNavigationClick = { onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }

  private fun handleAction(action: StickerPackPreviewAction) {
    when (action) {
      is StickerPackPreviewAction.SendPack -> openPackShareSheet(action.params)
      is StickerPackPreviewAction.ShareExternally -> openSystemShareSheet(action.params)
      StickerPackPreviewAction.LinkCopied -> {
        Toast.makeText(this, R.string.StickerManagement_copied, Toast.LENGTH_SHORT).show()
      }
      StickerPackPreviewAction.PackUnavailable -> {
        Toast.makeText(this, R.string.StickerPackPreviewActivity_failed_to_load_sticker_pack, Toast.LENGTH_SHORT).show()
        onBackPressedDispatcher.onBackPressed()
      }
      is StickerPackPreviewAction.SendSticker -> openStickerShareSheet(action.sticker)
    }
  }

  private fun openPackShareSheet(params: StickerPackParams) {
    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = supportFragmentManager,
      multiselectForwardFragmentArgs = MultiselectForwardFragmentArgs(
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDraftText(params.shareLink)
            .build()
        ),
        title = R.string.StickerManagement_share_sheet_title
      )
    )
  }

  private fun openStickerShareSheet(sticker: StickerManifest.Sticker) {
    val uri = sticker.uri.orNull() ?: return

    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = supportFragmentManager,
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

  private fun openSystemShareSheet(params: StickerPackParams) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, params.shareLink)
    }

    startActivity(Intent.createChooser(shareIntent, null))
  }
}

@Composable
private fun StickerPackPreviewScreen(
  uiState: StickerPackPreviewUiState,
  onEvent: (StickerPackPreviewEvent) -> Unit,
  onNavigationClick: () -> Unit
) {
  val gridState = rememberLazyGridState()
  val showToolbarDetails by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
  val toolbarAlpha by animateFloatAsState(targetValue = if (showToolbarDetails) 1f else 0f, label = "toolbar-alpha")

  val loadedState = uiState.contentState as? ContentState.HasData
  val stickerManifest = loadedState?.stickerManifest
  val untitledPackTitle = stringResource(R.string.StickerPackPreviewActivity_untitled)
  val menuController = remember { DropdownMenus.MenuController() }

  LaunchedEffect(showToolbarDetails) {
    if (!showToolbarDetails) {
      menuController.hide()
    }
  }

  Scaffolds.Settings(
    title = stickerManifest?.let { it.title.orNull() ?: untitledPackTitle } ?: "",
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
    titleContent = { _, toolbarTitle ->
      ToolbarTitle(
        title = toolbarTitle,
        alpha = toolbarAlpha,
        isBlessed = loadedState?.isBlessed ?: false
      )
    },
    actions = {
      if (loadedState != null && toolbarAlpha > 0f) {
        IconButton(
          onClick = { menuController.show() },
          modifier = Modifier
            .padding(horizontal = 8.dp)
            .alpha(toolbarAlpha)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
            contentDescription = stringResource(R.string.StickerManagement_accessibility_open_top_bar_menu)
          )
        }

        DropdownMenus.Menu(
          controller = menuController,
          offsetX = 24.dp,
          offsetY = 0.dp
        ) {
          DropdownMenus.ItemWithIcon(
            menuController = menuController,
            imageVector = SignalIcons.Forward.imageVector,
            stringResId = R.string.StickerManagement_menu_send_pack,
            onClick = { onEvent(StickerPackPreviewEvent.SendPackClicked) }
          )

          DropdownMenus.ItemWithIcon(
            menuController = menuController,
            imageVector = SignalIcons.Link.imageVector,
            stringResId = R.string.StickerManagement_menu_link_pack,
            onClick = { onEvent(StickerPackPreviewEvent.LinkPackClicked) }
          )

          if (loadedState.isPackInstalled) {
            DropdownMenus.ItemWithIcon(
              menuController = menuController,
              imageVector = SignalIcons.MinusCircle.imageVector,
              stringResId = R.string.StickerManagement_menu_remove_pack,
              onClick = { onEvent(StickerPackPreviewEvent.UninstallClicked) }
            )
          }
        }
      }
    },
    bottomBar = {
      if (loadedState != null && !loadedState.isPackInstalled) {
        InstallPackButton(
          onClick = { onEvent(StickerPackPreviewEvent.InstallClicked) }
        )
      }
    }
  ) { padding ->
    if (uiState.userPrompt is UserPrompt.ConfirmRemovePack) {
      Dialogs.SimpleAlertDialog(
        title = pluralStringResource(R.plurals.StickerManagement_delete_n_packs_confirmation, 1, 1),
        body = pluralStringResource(R.plurals.StickerManagement_delete_n_packs_confirmation_body, 1, 1),
        confirm = stringResource(R.string.StickerManagement_menu_remove_pack),
        confirmColor = MaterialTheme.colorScheme.error,
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(StickerPackPreviewEvent.UninstallConfirmed) },
        onDeny = { onEvent(StickerPackPreviewEvent.UninstallCanceled) },
        onDismissRequest = { onEvent(StickerPackPreviewEvent.UninstallCanceled) }
      )
    }

    if (uiState.userPrompt is UserPrompt.ShareStickerPack && stickerManifest != null) {
      StickerPackShareSheet(
        params = stickerManifest.params,
        stickerManifest = stickerManifest,
        onEvent = onEvent
      )
    }

    if (uiState.userPrompt is UserPrompt.PreviewSticker && stickerManifest != null) {
      StickerPreviewSheet(
        stickerManifest,
        uiState.userPrompt.sticker,
        loadedState.isPackInstalled,
        onEvent
      )
    }

    when (uiState.contentState) {
      is ContentState.Loading -> {
        Box(
          modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }

      is ContentState.HasData -> {
        StickerPackPreviewContent(
          contentState = uiState.contentState,
          gridState = gridState,
          onEvent = onEvent,
          modifier = Modifier.padding(padding)
        )
      }

      is ContentState.DataUnavailable -> Unit
    }
  }
}

@Composable
private fun ToolbarTitle(
  title: String,
  alpha: Float,
  isBlessed: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.alpha(alpha)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    if (isBlessed) {
      Image(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_official_20),
        contentDescription = stringResource(R.string.ConversationTitleView_verified),
        modifier = Modifier
          .padding(start = 4.dp)
          .size(20.dp)
      )
    }
  }
}

@Composable
private fun StickerPackPreviewContent(
  modifier: Modifier = Modifier,
  contentState: ContentState.HasData,
  gridState: LazyGridState,
  onEvent: (StickerPackPreviewEvent) -> Unit
) {
  LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Adaptive(minSize = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    contentPadding = PaddingValues(bottom = 12.dp, start = 16.dp, end = 16.dp),
    modifier = modifier.fillMaxSize()
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      StickerPackTitle(
        isBlessed = contentState.isBlessed,
        stickerManifest = contentState.stickerManifest,
        modifier = Modifier.fillMaxWidth()
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
      StickerPackInfo(
        stickerManifest = contentState.stickerManifest,
        modifier = Modifier.fillMaxWidth()
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
      StickerOptions(
        contentState = contentState,
        onEvent = onEvent,
        modifier = Modifier.padding(bottom = 24.dp)
      )
    }

    items(
      items = contentState.stickerManifest.stickers,
      key = { it.id }
    ) { item ->
      StickerImage(
        sticker = item,
        modifier = Modifier.size(96.dp),
        onClick = { onEvent(StickerPackPreviewEvent.StickerClicked(item)) }
      )
    }
  }
}

@Composable
private fun InstallPackButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Buttons.LargeTonal(
        content = { Text(text = stringResource(R.string.StickerManagement__add_stickers)) },
        onClick = onClick,
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 412.dp)
      )
    }
  }
}

@Composable
private fun StickerPackTitle(
  isBlessed: Boolean,
  modifier: Modifier = Modifier,
  stickerManifest: StickerManifest
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    stickerManifest.cover.orNull()?.let { cover ->
      StickerImage(
        sticker = cover,
        modifier = Modifier
          .padding(bottom = 12.dp)
          .size(80.dp)
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stickerManifest.title.orNull() ?: stringResource(R.string.StickerPackPreviewActivity_untitled),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
      )

      if (isBlessed) {
        Image(
          imageVector = ImageVector.vectorResource(id = R.drawable.ic_official_20),
          contentDescription = null,
          modifier = Modifier
            .padding(start = 6.dp)
            .size(24.dp)
        )
      }
    }
  }
}

@Composable
private fun StickerPackInfo(
  modifier: Modifier = Modifier,
  stickerManifest: StickerManifest
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stickerManifest.author
        .filter { it.isNotBlank() }
        .getOrElse { stringResource(R.string.StickerManagement_author_unknown) },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = pluralStringResource(
        R.plurals.StickerManagement_sticker_pack_preview_sticker_count,
        stickerManifest.stickers.size,
        NumberFormat.getInstance().format(stickerManifest.stickers.size)
      ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.size(12.dp))
  }
}

@Composable
private fun StickerOptions(
  contentState: ContentState.HasData,
  onEvent: (StickerPackPreviewEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Buttons.ActionButton(
      onClick = { onEvent(StickerPackPreviewEvent.SendPackClicked) },
      imageVector = SignalIcons.Forward.imageVector,
      label = stringResource(R.string.StickerManagement_menu_send_pack)
    )

    Spacer(modifier = Modifier.width(16.dp))

    Buttons.ActionButton(
      onClick = { onEvent(StickerPackPreviewEvent.LinkPackClicked) },
      imageVector = SignalIcons.Link.imageVector,
      label = stringResource(R.string.StickerManagement_menu_link_pack)
    )

    if (contentState.isPackInstalled) {
      Spacer(modifier = Modifier.width(16.dp))

      Buttons.ActionButton(
        onClick = { onEvent(StickerPackPreviewEvent.UninstallClicked) },
        imageVector = SignalIcons.MinusCircle.imageVector,
        label = stringResource(R.string.StickerManagement_menu_remove_pack)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPackShareSheet(
  params: StickerPackParams,
  stickerManifest: StickerManifest,
  onEvent: (StickerPackPreviewEvent) -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    sheetState = sheetState,
    onDismissRequest = { onEvent(StickerPackPreviewEvent.ShareSheetDismissed) }
  ) {
    StickerPackShareSheetContent(
      params = params,
      stickerManifest = stickerManifest,
      onForwardClick = { sheetState.dismissWithAnimation(scope, onComplete = { onEvent(StickerPackPreviewEvent.SendPackClicked) }) },
      onCopyClick = { sheetState.dismissWithAnimation(scope, onComplete = { onEvent(StickerPackPreviewEvent.CopyLinkClicked) }) },
      onShareExternallyClick = { sheetState.dismissWithAnimation(scope, onComplete = { onEvent(StickerPackPreviewEvent.ShareExternallyClicked) }) }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPreviewSheet(
  stickerManifest: StickerManifest,
  sticker: StickerManifest.Sticker,
  isPackInstalled: Boolean,
  onEvent: (StickerPackPreviewEvent) -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    sheetState = sheetState,
    onDismissRequest = { onEvent(StickerPackPreviewEvent.ShareSheetDismissed) }
  ) {
    StickerPreviewSheetContent(
      stickerManifest,
      sticker,
      isPackInstalled
    ) { sheetState.dismissWithAnimation(scope, onComplete = { onEvent(StickerPackPreviewEvent.StickerSent(sticker)) }) }
  }
}

@Composable
private fun StickerPackShareSheetContent(
  params: StickerPackParams,
  stickerManifest: StickerManifest,
  onForwardClick: () -> Unit,
  onCopyClick: () -> Unit,
  onShareExternallyClick: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.StickerManagement__anyone),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp)
    )

    Row(
      modifier = Modifier
        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), shape = RoundedCornerShape(12.dp))
        .padding(16.dp)
    ) {
      stickerManifest.cover.orNull()?.let { cover ->
        StickerImage(
          sticker = cover,
          modifier = Modifier.size(64.dp)
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp)
      ) {
        stickerManifest.title.orNull()?.let { title ->
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
          )
        }

        stickerManifest.author.orNull()?.let { author ->
          Text(
            text = author,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
          )
        }

        Text(
          text = params.shareLink,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Rows.TextRow(
      text = stringResource(R.string.StickerManagement_menu_send_signal_pack),
      icon = SignalIcons.Forward.imageVector,
      onClick = onForwardClick
    )

    Rows.TextRow(
      text = stringResource(R.string.StickerManagement_menu_copy_pack),
      icon = SignalIcons.Copy.imageVector,
      onClick = onCopyClick
    )

    Rows.TextRow(
      text = stringResource(R.string.StickerManagement_menu_share_pack),
      icon = SignalIcons.Share.imageVector,
      onClick = onShareExternallyClick
    )
  }
}

@Composable
fun StickerImage(
  modifier: Modifier = Modifier,
  sticker: StickerManifest.Sticker,
  onClick: (() -> Unit)? = null
) {
  if (!LocalInspectionMode.current) {
    GlideImage(
      model = sticker.imageModel,
      enableApngAnimation = true,
      modifier = if (onClick != null) {
        modifier.clickable(onClick = { onClick() })
      } else {
        modifier
      }
    )
  } else {
    Image(
      painter = painterResource(id = R.drawable.ic_avatar_tucan),
      contentDescription = null,
      modifier = modifier
    )
  }
}

@DayNightPreviews
@Composable
private fun HasDataPreview() {
  Previews.Preview {
    val cover = StickerManifest.Sticker(
      "packId0",
      "packKey0",
      0,
      "👍",
      null
    )

    StickerPackPreviewScreen(
      uiState = StickerPackPreviewUiState(
        contentState = ContentState.HasData(
          stickerManifest = StickerManifest(
            cover.packId,
            cover.packKey,
            "Misbrands (The world's most hated IT stickers extended)".toOptional(),
            "Sticker Pack Author".toOptional(),
            cover.toOptional(),
            StickerPreviewDataFactory.manifestStickers(33)
          ),
          isPackInstalled = false,
          isBlessed = true
        )
      ),
      onEvent = {},
      onNavigationClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun HasDataPreviewInstalled() {
  Previews.Preview {
    val cover = StickerManifest.Sticker(
      "packId0",
      "packKey0",
      0,
      "👍",
      null
    )

    StickerPackPreviewScreen(
      uiState = StickerPackPreviewUiState(
        contentState = ContentState.HasData(
          stickerManifest = StickerManifest(
            cover.packId,
            cover.packKey,
            "Misbrands (The world's most hated IT stickers extended)".toOptional(),
            "Sticker Pack Author".toOptional(),
            cover.toOptional(),
            StickerPreviewDataFactory.manifestStickers(33)
          ),
          isPackInstalled = true,
          isBlessed = true
        )
      ),
      onEvent = {},
      onNavigationClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun StickerPackShareSheetContentPreview() {
  Previews.BottomSheetContentPreview {
    val cover = StickerManifest.Sticker("packId0", "packKey0", 0, "👍", null)

    StickerPackShareSheetContent(
      params = StickerPackParams(StickerPackId(cover.packId), StickerPackKey(cover.packKey)),
      stickerManifest = StickerManifest(
        cover.packId,
        cover.packKey,
        "Misbrands".toOptional(),
        "Sticker Pack Author".toOptional(),
        cover.toOptional(),
        StickerPreviewDataFactory.manifestStickers(33)
      ),
      onForwardClick = {},
      onCopyClick = {},
      onShareExternallyClick = {}
    )
  }
}

private val StickerManifest.Sticker.imageModel: Key
  get() = uri
    .map(::DecryptableUri)
    .getOrElse { StickerRemoteUri(packId, packKey, id) }

private val StickerManifest.params: StickerPackParams
  get() = StickerPackParams(
    id = StickerPackId(packId),
    key = StickerPackKey(packKey)
  )

private val StickerPackParams.shareLink: String
  get() = StickerUrl.createShareLink(id.value, key.value)
