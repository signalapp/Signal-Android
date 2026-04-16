package org.thoughtcrime.securesms.mediaoverview

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONArray
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
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
          getForwardActionItem(mediaRecord),
          getShareActionItem(mediaRecord),
          getSaveActionItem(mediaRecord),
          getDeleteActionItem(mediaRecord),
          getSelectActionItem(mediaRecord),
          getJumpToMessageActionItem(mediaRecord)
        )
      )
  }

  private fun getForwardActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem? {
    if (mediaRecord.linkPreviewJson != null) {
      return null
    }

    val uri = mediaRecord.attachment?.uri ?: return null
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_forward_24,
      title = fragment.getString(R.string.StickerManagement_menu_forward_pack)
    ) {
      MultiselectForwardFragmentArgs.create(
        context = fragment.requireContext(),
        threadId = mediaRecord.threadId,
        mediaUri = uri,
        contentType = mediaRecord.attachment?.contentType
      ) { args ->
        MultiselectForwardFragment.showBottomSheet(fragment.childFragmentManager, args)
      }
    }
  }

  private fun getShareActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem? {
    if (mediaRecord.linkPreviewJson != null) {
      return getShareLinkActionItem(mediaRecord)
    }

    val attachment = mediaRecord.attachment ?: return null
    val uri = attachment.uri ?: return null
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_share_android_24,
      title = fragment.getString(R.string.InviteActivity_share)
    ) {
      val publicUri = PartAuthority.getAttachmentPublicUri(uri)
      val mimeType = Intent.normalizeMimeType(attachment.contentType)
      val shareIntent = ShareCompat.IntentBuilder(fragment.requireActivity())
        .setStream(publicUri)
        .setType(mimeType)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      if (Build.VERSION.SDK_INT < 34) {
        shareIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(fragment.requireContext(), ShareActivity::class.java)))
      }

      try {
        fragment.startActivity(shareIntent)
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity found to share media", e)
        Toast.makeText(fragment.requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun getShareLinkActionItem(mediaRecord: MediaTable.MediaRecord): ActionItem? {
    val url = try {
      val jsonPreviews = JSONArray(mediaRecord.linkPreviewJson)
      LinkPreview.deserialize(jsonPreviews.getJSONObject(0).toString()).url
    } catch (e: Exception) {
      Log.w(TAG, "Failed to deserialize link preview", e)
      return null
    }

    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_share_android_24,
      title = fragment.getString(R.string.InviteActivity_share)
    ) {
      val shareIntent = ShareCompat.IntentBuilder(fragment.requireActivity())
        .setText(url)
        .setType("text/plain")
        .createChooserIntent()

      if (Build.VERSION.SDK_INT < 34) {
        shareIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(fragment.requireContext(), ShareActivity::class.java)))
      }

      try {
        fragment.startActivity(shareIntent)
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity found to share link", e)
        Toast.makeText(fragment.requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
      }
    }
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

  companion object {
    private val TAG = Log.tag(MediaOverviewContextMenu::class.java)
  }
}
