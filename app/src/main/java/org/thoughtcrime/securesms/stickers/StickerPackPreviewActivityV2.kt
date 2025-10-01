/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.load.Key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackParams
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stickers.StickerPackPreviewUiState.ContentState
import org.thoughtcrime.securesms.stickers.StickerPackPreviewUiState.NavTarget
import org.thoughtcrime.securesms.util.DeviceProperties
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
      uiState.navTarget?.let(::navigateToTarget)
      uiState.userMessage?.let(::showUserMessage)

      SignalTheme {
        StickerPackPreviewScreen(
          uiState = uiState,
          callbacks = object : StickerPackPreviewScreenCallbacks {
            override fun onNavigateTo(target: NavTarget) = navigateToTarget(target)
            override fun onForwardClick(params: StickerPackParams) = openShareSheet(params)
            override fun onInstallClick(params: StickerPackParams) = viewModel.installStickerPack(params)
            override fun onUninstallClick(params: StickerPackParams) = viewModel.uninstallStickerPack(params)
          }
        )
      }
    }
  }

  private fun navigateToTarget(target: NavTarget) {
    lifecycleScope.launch {
      when (target) {
        is NavTarget.Up -> {
          lifecycleScope.launch {
            if (target.delay != null) delay(target.delay.inWholeMilliseconds)
            onBackPressedDispatcher.onBackPressed()
          }
        }
      }
      viewModel.setNavTargetConsumed()
    }
  }

  private fun showUserMessage(type: StickerPackPreviewUiState.MessageType) {
    when (type) {
      StickerPackPreviewUiState.MessageType.STICKER_PACK_LOAD_FAILED -> {
        Toast.makeText(this, R.string.StickerPackPreviewActivity_failed_to_load_sticker_pack, Toast.LENGTH_SHORT).show()
      }
    }
    viewModel.setUserMessageConsumed()
  }

  private fun openShareSheet(params: StickerPackParams) {
    supportFragmentManager.setFragmentResultListener(MultiselectForwardFragment.RESULT_KEY, this) { _: String?, result: Bundle ->
      if (result.getBoolean(MultiselectForwardFragment.RESULT_SENT, false)) {
        navigateToTarget(NavTarget.Up())
      }
    }

    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = supportFragmentManager,
      multiselectForwardFragmentArgs = MultiselectForwardFragmentArgs(
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDraftText(StickerUrl.createShareLink(params.id.value, params.key.value))
            .build()
        ),
        title = R.string.StickerManagement_share_sheet_title
      )
    )
  }
}

interface StickerPackPreviewScreenCallbacks {
  fun onNavigateTo(target: NavTarget)
  fun onForwardClick(params: StickerPackParams)
  fun onInstallClick(params: StickerPackParams)
  fun onUninstallClick(params: StickerPackParams)

  object Empty : StickerPackPreviewScreenCallbacks {
    override fun onNavigateTo(target: NavTarget) = Unit
    override fun onForwardClick(params: StickerPackParams) = Unit
    override fun onInstallClick(params: StickerPackParams) = Unit
    override fun onUninstallClick(params: StickerPackParams) = Unit
  }
}

@Composable
private fun StickerPackPreviewScreen(
  uiState: StickerPackPreviewUiState,
  callbacks: StickerPackPreviewScreenCallbacks
) {
  Scaffold(
    topBar = {
      TopAppBar(
        stickerManifest = if (uiState.contentState is ContentState.HasData) uiState.contentState.stickerManifest else null,
        onNavigateUp = { callbacks.onNavigateTo(NavTarget.Up()) },
        onForwardClick = callbacks::onForwardClick
      )
    }
  ) { padding ->
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
        uiState.navTarget?.let(callbacks::onNavigateTo)

        StickerPackPreviewContent(
          contentState = uiState.contentState,
          onInstallClick = callbacks::onInstallClick,
          onUninstallClick = callbacks::onUninstallClick,
          modifier = Modifier.padding(padding)
        )
      }

      is ContentState.DataUnavailable -> Unit
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
  stickerManifest: StickerManifest?,
  onNavigateUp: () -> Unit,
  onForwardClick: (params: StickerPackParams) -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = "", // TODO collapse title into top app bar on scroll
    titleContent = { _, text -> Text(text = text, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
    onNavigationClick = onNavigateUp,
    actions = {
      if (stickerManifest != null) {
        IconButton(
          onClick = { onForwardClick(stickerManifest.params) },
          modifier = Modifier.padding(horizontal = 8.dp)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_forward_24),
            contentDescription = stringResource(R.string.StickerManagement_menu_forward_pack)
          )
        }
      }
    }
  )
}

@Composable
private fun StickerPackPreviewContent(
  modifier: Modifier = Modifier,
  contentState: ContentState.HasData,
  onInstallClick: (params: StickerPackParams) -> Unit,
  onUninstallClick: (params: StickerPackParams) -> Unit
) {
  Column(
    modifier = modifier.padding(top = 0.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
  ) {
    StickerPackInfo(
      stickerManifest = contentState.stickerManifest,
      modifier = Modifier.fillMaxWidth()
    )

    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 96.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      modifier = Modifier
        .padding(top = 32.dp)
        .weight(1f)
    ) {
      items(
        items = contentState.stickerManifest.stickers,
        key = { it.id }
      ) { item ->
        StickerImage(
          sticker = item,
          modifier = Modifier.size(96.dp)
        )
      }
    }

    val actionButtonModifier = Modifier
      .width(412.dp)
      .align(Alignment.CenterHorizontally)
      .padding(top = 12.dp)

    if (contentState.isPackInstalled) {
      Buttons.LargeTonal(
        content = { Text(text = stringResource(R.string.StickerManagement_menu_remove_pack)) },
        onClick = { onUninstallClick(contentState.stickerManifest.params) },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = actionButtonModifier
      )
    } else {
      Buttons.LargeTonal(
        content = { Text(text = stringResource(R.string.StickerManagement_menu_install_pack)) },
        onClick = { onInstallClick(contentState.stickerManifest.params) },
        modifier = actionButtonModifier
      )
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
    stickerManifest.cover.orNull()?.let { cover ->
      StickerImage(
        sticker = cover,
        modifier = Modifier
          .padding(bottom = 12.dp)
          .size(80.dp)
      )
    }

    stickerManifest.title.orNull()?.let { title ->
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium
        )

        if (BlessedPacks.contains(stickerManifest.packId)) {
          Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_official_20),
            contentDescription = null,
            modifier = Modifier
              .padding(horizontal = 6.dp)
              .size(24.dp)
          )
        }
      }
    }

    Text(
      text = stickerManifest.author
        .filter { it.isNotBlank() }
        .getOrElse { stringResource(R.string.StickerManagement_author_unknown) },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp)
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
  }
}

@Composable
private fun StickerImage(
  modifier: Modifier = Modifier,
  sticker: StickerManifest.Sticker
) {
  if (!LocalInspectionMode.current) {
    GlideImage(
      model = sticker.imageModel,
      enableApngAnimation = DeviceProperties.shouldAllowApngStickerAnimation(LocalContext.current),
      modifier = modifier
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
          isPackInstalled = false
        )
      ),
      callbacks = StickerPackPreviewScreenCallbacks.Empty
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
