package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TooltipPopup
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Any and all tooltips that the conversation can display, and a light amount of related presentation logic.
 */
class ConversationTooltips(fragment: Fragment) {
  companion object {
    private val TAG = Log.tag(ConversationTooltips::class.java)
  }

  private val viewModel: TooltipViewModel by fragment.viewModels()

  /**
   *  Displayed to teach the user about sticker packs
   */
  fun displayStickerIntroductionTooltip(anchor: View, onDismiss: () -> Unit) {
    TooltipPopup.forTarget(anchor)
      .setBackgroundTint(ContextCompat.getColor(anchor.context, R.color.core_ultramarine))
      .setTextColor(ContextCompat.getColor(anchor.context, R.color.core_white))
      .setText(R.string.ConversationActivity_new_say_it_with_stickers)
      .setOnDismissListener {
        TextSecurePreferences.setHasSeenStickerIntroTooltip(anchor.context, true)
        onDismiss()
      }
      .show(TooltipPopup.POSITION_ABOVE)
  }

  /**
   * Displayed after a sticker pack is installed
   */
  fun displayStickerPackInstalledTooltip(anchor: View, event: StickerPackInstallEvent) {
    TooltipPopup.forTarget(anchor)
      .setText(R.string.ConversationActivity_sticker_pack_installed)
      .setIconGlideModel(event.iconGlideModel)
      .show(TooltipPopup.POSITION_ABOVE)
  }

  /**
   * ViewModel which holds different bits of session-local persistent state for different tooltips.
   */
  class TooltipViewModel : ViewModel() {
    var hasDisplayedCallingTooltip: Boolean = false
  }
}
