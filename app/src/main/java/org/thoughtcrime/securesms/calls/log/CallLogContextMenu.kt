package org.thoughtcrime.securesms.calls.log

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsActivity
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Context menu for row items on the Call Log screen.
 */
class CallLogContextMenu(
  private val fragment: Fragment,
  private val callbacks: Callbacks
) {

  private val lifecycleDisposable by lazy { LifecycleDisposable().bindTo(fragment.viewLifecycleOwner) }

  fun show(recyclerView: RecyclerView, anchor: View, call: CallLogRow.Call) {
    recyclerView.suppressLayout(true)
    anchor.isSelected = true
    SignalContextMenu.Builder(anchor, anchor.parent as ViewGroup)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .onDismiss {
        anchor.isSelected = false
        recyclerView.suppressLayout(false)
      }
      .show(
        listOfNotNull(
          getVideoCallActionItem(call.peer),
          getAudioCallActionItem(call),
          getGoToChatActionItem(call),
          getInfoActionItem(call.peer, (call.id as CallLogRow.Id.Call).children.toLongArray()),
          getSelectActionItem(call),
          getDeleteActionItem(call)
        )
      )
  }

  fun show(recyclerView: RecyclerView, anchor: View, callLink: CallLogRow.CallLink) {
    recyclerView.suppressLayout(true)
    anchor.isSelected = true
    SignalContextMenu.Builder(anchor, anchor.parent as ViewGroup)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .onDismiss {
        anchor.isSelected = false
        recyclerView.suppressLayout(false)
      }
      .show(
        listOfNotNull(
          getVideoCallActionItem(callLink.recipient),
          getInfoActionItem(callLink.recipient, longArrayOf()),
          getSelectActionItem(callLink),
          getDeleteActionItem(callLink)
        )
      )
  }

  private fun getVideoCallActionItem(peer: Recipient): ActionItem {
    // TODO [alex] -- Need group calling disposition to make this correct
    return ActionItem(
      iconRes = R.drawable.symbol_video_24,
      title = fragment.getString(R.string.CallContextMenu__video_call)
    ) {
      CommunicationActions.startVideoCall(fragment, peer) {
        YouAreAlreadyInACallSnackbar.show(fragment.requireView())
      }
    }
  }

  private fun getAudioCallActionItem(call: CallLogRow.Call): ActionItem? {
    if (call.peer.isCallLink || call.peer.isGroup) {
      return null
    }

    return ActionItem(
      iconRes = R.drawable.symbol_phone_24,
      title = fragment.getString(R.string.CallContextMenu__audio_call)
    ) {
      CommunicationActions.startVoiceCall(fragment, call.peer) {
        YouAreAlreadyInACallSnackbar.show(fragment.requireView())
      }
    }
  }

  private fun getGoToChatActionItem(call: CallLogRow.Call): ActionItem? {
    return when {
      call.peer.isCallLink -> null
      else -> ActionItem(
        iconRes = R.drawable.symbol_open_24,
        title = fragment.getString(R.string.CallContextMenu__go_to_chat)
      ) {
        lifecycleDisposable += ConversationIntents.createBuilder(fragment.requireContext(), call.peer.id, -1L)
          .subscribeBy {
            fragment.startActivity(it.build())
          }
      }
    }
  }

  private fun getInfoActionItem(peer: Recipient, messageIds: LongArray): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_info_24,
      title = fragment.getString(R.string.CallContextMenu__info)
    ) {
      val intent = when {
        peer.isCallLink -> CallLinkDetailsActivity.createIntent(fragment.requireContext(), peer.requireCallLinkRoomId())
        else -> ConversationSettingsActivity.forCall(fragment.requireContext(), peer, messageIds)
      }
      fragment.startActivity(intent)
    }
  }

  private fun getSelectActionItem(call: CallLogRow): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_check_circle_24,
      title = fragment.getString(R.string.CallContextMenu__select)
    ) {
      callbacks.startSelection(call)
    }
  }

  private fun getDeleteActionItem(call: CallLogRow): ActionItem? {
    if (call is CallLogRow.Call && call.record.event == CallTable.Event.ONGOING) {
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
    fun startSelection(call: CallLogRow)
    fun deleteCall(call: CallLogRow)
  }
}
