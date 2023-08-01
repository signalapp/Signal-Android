package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TooltipPopup
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
   * Displays the tooltip notifying the user that they can begin a group call. Also
   * performs the necessary record-keeping and checks to ensure we don't display it
   * if we shouldn't. There is a set of callbacks which should be used to preserve
   * session state for this tooltip.
   *
   * @param anchor The view this will be displayed underneath. If the view is not ready, we will skip.
   */
  fun displayGroupCallingTooltip(
    anchor: View?
  ) {
    if (viewModel.hasDisplayedCallingTooltip || !SignalStore.tooltips().shouldShowGroupCallingTooltip()) {
      return
    }

    if (anchor == null) {
      Log.w(TAG, "Group calling tooltip anchor is null. Skipping tooltip.")
      return
    }

    viewModel.hasDisplayedCallingTooltip = true

    SignalStore.tooltips().markGroupCallSpeakerViewSeen()
    TooltipPopup.forTarget(anchor)
      .setBackgroundTint(ContextCompat.getColor(anchor.context, R.color.signal_accent_green))
      .setTextColor(ContextCompat.getColor(anchor.context, R.color.core_white))
      .setText(R.string.ConversationActivity__tap_here_to_start_a_group_call)
      .setOnDismissListener { SignalStore.tooltips().markGroupCallingTooltipSeen() }
      .show(TooltipPopup.POSITION_BELOW)
  }

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
