/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import org.signal.core.ui.compose.copied.androidx.compose.DragAndDropEvent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.sharing.MultiShareArgs
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
    }
  }

  @Composable
  override fun SheetContent() {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        onNavigateBack = ::dismiss,
        onSetMultiSelectModeEnabled = viewModel::setMultiSelectEnabled,
        onSnackbarDismiss = viewModel::onSnackbarDismiss,
        availableTabCallbacks = remember {
          object : AvailableStickersContentCallbacks {
            override fun onForwardClick(pack: AvailableStickerPack) = openShareSheet(pack.id, pack.key)
            override fun onInstallClick(pack: AvailableStickerPack) = viewModel.installStickerPack(pack)
            override fun onShowPreviewClick(pack: AvailableStickerPack) = navigateToStickerPreview(pack.id, pack.key)
          }
        },
        installedTabCallbacks = remember {
          object : InstalledStickersContentCallbacks {
            override fun onForwardClick(pack: InstalledStickerPack) = openShareSheet(pack.id, pack.key)
            override fun onRemoveClick(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksRequested(packIds)
            override fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksConfirmed(packIds)
            override fun onRemoveStickerPacksCanceled() = viewModel.onUninstallStickerPacksCanceled()
            override fun onSelectionToggle(pack: InstalledStickerPack) = viewModel.toggleSelection(pack)
            override fun onSelectAllToggle() = viewModel.toggleSelectAll()
            override fun onDragAndDropEvent(event: DragAndDropEvent) {
              when (event) {
                is DragAndDropEvent.OnItemMove -> viewModel.updatePosition(event.fromIndex, event.toIndex)
                is DragAndDropEvent.OnItemDrop -> viewModel.saveInstalledPacksSortOrder()
                is DragAndDropEvent.OnDragCancel -> {}
              }
            }

            override fun onShowPreviewClick(pack: InstalledStickerPack) = navigateToStickerPreview(pack.id, pack.key)
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
