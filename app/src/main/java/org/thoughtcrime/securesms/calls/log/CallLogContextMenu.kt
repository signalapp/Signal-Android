package org.thoughtcrime.securesms.calls.log

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Context menu for row items on the Call Log screen.
 */
class CallLogContextMenu(
  private val fragment: Fragment,
  private val callbacks: Callbacks
) {
  fun show(anchor: View, call: CallLogRow.Call) {
    anchor.isSelected = true
    SignalContextMenu.Builder(anchor, anchor.parent as ViewGroup)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .onDismiss { anchor.isSelected = false }
      .show(
        listOfNotNull(
          getVideoCallActionItem(call),
          getAudioCallActionItem(call),
          getGoToChatActionItem(call),
          getInfoActionItem(call),
          getSelectActionItem(call),
          getDeleteActionItem(call)
        )
      )
  }

  private fun getVideoCallActionItem(call: CallLogRow.Call): ActionItem {
    // TODO [alex] -- Need group calling disposition to make this correct
    return ActionItem(
      iconRes = R.drawable.symbol_video_24,
      title = fragment.getString(R.string.CallContextMenu__video_call)
    ) {
      CommunicationActions.startVideoCall(fragment, call.peer)
    }
  }

  private fun getAudioCallActionItem(call: CallLogRow.Call): ActionItem? {
    if (call.peer.isGroup) {
      return null
    }

    return ActionItem(
      iconRes = R.drawable.symbol_phone_24,
      title = fragment.getString(R.string.CallContextMenu__audio_call)
    ) {
      CommunicationActions.startVoiceCall(fragment, call.peer)
    }
  }

  private fun getGoToChatActionItem(call: CallLogRow.Call): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_open_24,
      title = fragment.getString(R.string.CallContextMenu__go_to_chat)
    ) {
      fragment.startActivity(ConversationIntents.createBuilder(fragment.requireContext(), call.peer.id, -1L).build())
    }
  }

  private fun getInfoActionItem(call: CallLogRow.Call): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_info_24,
      title = fragment.getString(R.string.CallContextMenu__info)
    ) {
      val intent = ConversationSettingsActivity.forCall(fragment.requireContext(), call.peer, longArrayOf(call.record.messageId!!))
      fragment.startActivity(intent)
    }
  }

  private fun getSelectActionItem(call: CallLogRow.Call): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_check_circle_24,
      title = fragment.getString(R.string.CallContextMenu__select)
    ) {
      callbacks.startSelection(call)
    }
  }

  private fun getDeleteActionItem(call: CallLogRow.Call): ActionItem? {
    if (call.record.event == CallTable.Event.ONGOING) {
      return null
    }

    return ActionItem(
      iconRes = R.drawable.symbol_trash_24,
      title = fragment.getString(R.string.CallContextMenu__delete)
    ) {
      callbacks.deleteCall(call)
    }
  }

  interface Callbacks {
    fun startSelection(call: CallLogRow.Call)
    fun deleteCall(call: CallLogRow.Call)
  }
}
