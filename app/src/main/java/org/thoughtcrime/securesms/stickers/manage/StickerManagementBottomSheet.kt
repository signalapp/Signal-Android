/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.manage

import android.app.Dialog
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.list.ReorderListEvent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stickers.StickerUrl
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewActivity
import org.thoughtcrime.securesms.util.viewModel

/**
 * Bottom sheet implementation of [StickerManagementScreen].
 */
class StickerManagementBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val TAG = "StickerManagementSheet"

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      StickerManagementBottomSheet().show(fragmentManager, TAG)
    }
  }

  private val viewModel by viewModel { StickerManagementViewModel() }

  private val backPressCallback = object : OnBackPressedCallback(enabled = false) {
    override fun handleOnBackPressed() {
      if (viewModel.uiState.value.multiSelectEnabled) {
        viewModel.setMultiSelectEnabled(false)
      } else {
        viewModel.setSearchModeEnabled(false)
      }
    }
  }

  override val applyImePadding = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.onScreenVisible()
      }
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
    return dialog.apply {
      behavior.skipCollapsed = true
      behavior.state = BottomSheetBehavior.STATE_EXPANDED
      onBackPressedDispatcher.addCallback(backPressCallback)
    }
  }

  @Composable
  override fun SheetContent() {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.multiSelectEnabled, uiState.searchMode) {
      backPressCallback.isEnabled = uiState.multiSelectEnabled || uiState.searchMode
    }

    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
      ) {
        BottomSheets.Handle()
      }

      StickerManagementScreen(
        uiState = uiState,
        showNavigateBack = false,
        applyWindowInsets = false,
        onNavigateBack = ::dismiss,
        onSetMultiSelectModeEnabled = viewModel::setMultiSelectEnabled,
        onSetSearchModeEnabled = viewModel::setSearchModeEnabled,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onSnackbarDismiss = viewModel::onSnackbarDismiss,
        callbacks = remember {
          object : StickerManagementContentCallbacks {
            override fun onForwardClick(pack: StickerPack) = openShareSheet(pack.id, pack.key)
            override fun onInstallClick(pack: StickerPack) = viewModel.installStickerPack(pack)
            override fun onShowPreviewClick(pack: StickerPack) = navigateToStickerPreview(pack.id, pack.key)
            override fun onCopyClick(pack: StickerPack) = viewModel.onCopyPack(pack.id, pack.key)
            override fun onRemoveClick(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksRequested(packIds)
            override fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksConfirmed(packIds)
            override fun onRemoveStickerPacksCanceled() = viewModel.onUninstallStickerPacksCanceled()
            override fun onSelectionToggle(pack: StickerPack) = viewModel.toggleSelection(pack)
            override fun onSelectAllToggle() = viewModel.toggleSelectAll()
            override fun onReorderableEvent(event: ReorderListEvent) {
              when (event) {
                is ReorderListEvent.ItemMoved -> viewModel.updatePosition(event.fromIndex, event.toIndex)
                is ReorderListEvent.ItemDropped -> viewModel.saveInstalledPacksSortOrder()
                is ReorderListEvent.DragCanceled -> {}
              }
            }
          }
        }
      )
    }
  }

  private fun openShareSheet(packId: StickerPackId, packKey: StickerPackKey) {
    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = parentFragmentManager,
      multiselectForwardFragmentArgs = MultiselectForwardFragmentArgs(
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDraftText(StickerUrl.createShareLink(packId.value, packKey.value))
            .build()
        ),
        title = R.string.StickerManagement_share_sheet_title
      )
    )
  }

  private fun navigateToStickerPreview(packId: StickerPackId, packKey: StickerPackKey) {
    startActivity(StickerPackPreviewActivity.getIntent(packId.value, packKey.value))
  }
}
