package org.thoughtcrime.securesms.mediaoverview

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.signal.core.ui.R as CoreUiR

/**
 * Context menu shown when long-pressing a media item in [MediaOverviewPageFragment].
 */
class MediaOverviewContextMenu(
  private val fragment: Fragment,
  private val callbacks: Callbacks
) {

  private val lifecycleDisposable by lazy { LifecycleDisposable().bindTo(fragment.viewLifecycleOwner) }

  fun show(anchor: View, mediaRecord: MediaTable.MediaRecord) {
    val recyclerView = anchor.parent as? RecyclerView
    recyclerView?.suppressLayout(true)
    anchor.isSelected = true

    SignalContextMenu.Builder(anchor, anchor.parent as ViewGroup)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .offsetY(4.dp)
      .onDismiss {
        anchor.isSelected = false
        recyclerView?.suppressLayout(false)
      }
      .show(
        listOfNotNull(
          getSaveActionItem(mediaRecord),
          getDeleteActionItem(mediaRecord),
          getSelectActionItem(mediaRecord),
          getJumpToMessageActionItem(mediaRecord)
        )
      )
  }

  private fun getSaveActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem? {
    if (mediaRecord.attachment == null) return null
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_save_android_24,
      title = fragment.getString(R.string.save)
    ) {
      callbacks.onSave(mediaRecord)
    }
  }

  private fun getDeleteActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem {
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_trash_24,
      title = fragment.getString(R.string.delete)
    ) {
      callbacks.onDelete(mediaRecord)
    }
  }

  private fun getSelectActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem {
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_check_circle_24,
      title = fragment.getString(R.string.CallContextMenu__select)
    ) {
      callbacks.onSelect(mediaRecord)
    }
  }

  private fun getJumpToMessageActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem {
    return ActionItem(
      iconRes = R.drawable.symbol_open_24,
      title = fragment.getString(R.string.MediaOverviewActivity_jump_to_message)
    ) {
      lifecycleDisposable += Single.fromCallable<Int> {
        val dateReceived = SignalDatabase.messages.getMessageRecordOrNull(mediaRecord.messageId)?.dateReceived
          ?: mediaRecord.date
        SignalDatabase.messages.getMessagePositionInConversation(mediaRecord.threadId, dateReceived)
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { position ->
          fragment.startActivity(
            ConversationIntents.createBuilderSync(fragment.requireContext(), mediaRecord.threadRecipientId, mediaRecord.threadId)
              .withStartingPosition(maxOf(0, position))
              .build()
          )
        }
    }
  }

  interface Callbacks {
    fun onSave(mediaRecord: MediaTable.MediaRecord)
    fun onDelete(mediaRecord: MediaTable.MediaRecord)
    fun onSelect(mediaRecord: MediaTable.MediaRecord)
  }
}
