/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard.sticker

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.ComposeFullScreenDialogFragment
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.screens.stickersearch.StickerSearch
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mediakeyboard.SignalStickerKeyboardRepository
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Full-screen [StickerSearch] over the media keyboard, handing whatever the user does in it back to the [Callback] as
 * the keyboard itself would.
 */
class StickerSearchDialogFragment : ComposeFullScreenDialogFragment() {

  override val padForSafeDrawing: Boolean = false

  override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
    return super.onGetLayoutInflater(savedInstanceState).also {
      dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
  }

  @Composable
  override fun DialogContent() {
    val repository = remember { SignalStickerKeyboardRepository(AppDependencies.application) }

    StickerSearch(
      repository = repository,
      onAction = { action ->
        if (action is MediaKeyboardAction.StickerSendClicked) {
          dismissAllowingStateLoss()
        }

        findListener<Callback>()?.onStickerSearchAction(action)
      },
      onClose = ::dismissAllowingStateLoss
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    findListener<Callback>()?.onStickerSearchDismissed()
  }

  interface Callback {
    /** Something the user did in the search that only the host can carry out, as from the keyboard's sticker page. */
    fun onStickerSearchAction(action: MediaKeyboardAction)

    /** For a host that has to put something back once this window is gone. */
    fun onStickerSearchDismissed()
  }

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      StickerSearchDialogFragment().show(fragmentManager, "TAG")
    }
  }
}
