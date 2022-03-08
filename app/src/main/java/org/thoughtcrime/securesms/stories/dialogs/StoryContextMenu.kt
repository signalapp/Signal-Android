package org.thoughtcrime.securesms.stories.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.stories.landing.StoriesLandingItem
import org.thoughtcrime.securesms.stories.my.MyStoriesItem
import org.thoughtcrime.securesms.stories.viewer.page.StoryPost
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageState
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.SaveAttachmentTask

object StoryContextMenu {

  private val TAG = Log.tag(StoryContextMenu::class.java)

  fun delete(context: Context, records: Set<MessageRecord>): Single<Boolean> {
    return DeleteDialog.show(
      context = context,
      messageRecords = records,
      title = context.getString(R.string.MyStories__delete_story),
      message = context.getString(R.string.MyStories__this_story_will_be_deleted),
      forceRemoteDelete = true
    )
  }

  fun save(context: Context, messageRecord: MessageRecord) {
    val mediaMessageRecord = messageRecord as? MediaMmsMessageRecord
    val uri: Uri? = mediaMessageRecord?.slideDeck?.firstSlide?.uri
    val contentType: String? = mediaMessageRecord?.slideDeck?.firstSlide?.contentType
    if (uri == null || contentType == null) {
      // TODO [stories] Toast that we can't save this media
      return
    }

    val saveAttachment = SaveAttachmentTask.Attachment(
      uri,
      contentType,
      mediaMessageRecord.dateSent,
      null
    )

    SaveAttachmentTask(context)
      .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, saveAttachment)
  }

  fun share(fragment: Fragment, messageRecord: MediaMmsMessageRecord) {
    val attachment: Attachment = messageRecord.slideDeck.firstSlide!!.asAttachment()
    val intent: Intent = ShareCompat.IntentBuilder(fragment.requireContext())
      .setStream(attachment.publicUri)
      .setType(attachment.contentType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
      fragment.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to share the media.", e)
      Toast.makeText(fragment.requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }

  fun show(
    context: Context,
    anchorView: View,
    model: StoriesLandingItem.Model,
    onDismiss: () -> Unit
  ) {
    show(
      context = context,
      anchorView = anchorView,
      isFromSelf = model.data.primaryStory.messageRecord.isOutgoing,
      isToGroup = model.data.storyRecipient.isGroup,
      canHide = !model.data.isHidden,
      callbacks = object : Callbacks {
        override fun onHide() = model.onHideStory(model)
        override fun onUnhide() = model.onHideStory(model)
        override fun onForward() = model.onForwardStory(model)
        override fun onShare() = model.onShareStory(model)
        override fun onGoToChat() = model.onGoToChat(model)
        override fun onDismissed() = onDismiss()
        override fun onDelete() = model.onDeleteStory(model)
        override fun onSave() = model.onSave(model)
      }
    )
  }

  fun show(
    context: Context,
    anchorView: View,
    storyViewerPageState: StoryViewerPageState,
    onHide: (StoryPost) -> Unit,
    onForward: (StoryPost) -> Unit,
    onShare: (StoryPost) -> Unit,
    onGoToChat: (StoryPost) -> Unit,
    onSave: (StoryPost) -> Unit,
    onDelete: (StoryPost) -> Unit,
    onDismiss: () -> Unit
  ) {
    val selectedStory: StoryPost = storyViewerPageState.posts[storyViewerPageState.selectedPostIndex]
    show(
      context = context,
      anchorView = anchorView,
      isFromSelf = selectedStory.sender.isSelf,
      isToGroup = selectedStory.group != null,
      canHide = true,
      callbacks = object : Callbacks {
        override fun onHide() = onHide(selectedStory)
        override fun onUnhide() = throw NotImplementedError()
        override fun onForward() = onForward(selectedStory)
        override fun onShare() = onShare(selectedStory)
        override fun onGoToChat() = onGoToChat(selectedStory)
        override fun onDismissed() = onDismiss()
        override fun onSave() = onSave(selectedStory)
        override fun onDelete() = onDelete(selectedStory)
      }
    )
  }

  fun show(
    context: Context,
    anchorView: View,
    myStoriesItemModel: MyStoriesItem.Model,
    onDismiss: () -> Unit
  ) {
    show(
      context = context,
      anchorView = anchorView,
      isFromSelf = true,
      isToGroup = false,
      canHide = false,
      callbacks = object : Callbacks {
        override fun onHide() = throw NotImplementedError()
        override fun onUnhide() = throw NotImplementedError()
        override fun onForward() = myStoriesItemModel.onForwardClick(myStoriesItemModel)
        override fun onShare() = myStoriesItemModel.onShareClick(myStoriesItemModel)
        override fun onGoToChat() = throw NotImplementedError()
        override fun onDismissed() = onDismiss()
        override fun onSave() = myStoriesItemModel.onSaveClick(myStoriesItemModel)
        override fun onDelete() = myStoriesItemModel.onDeleteClick(myStoriesItemModel)
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    isFromSelf: Boolean,
    isToGroup: Boolean,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    canHide: Boolean,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      if (!isFromSelf || isToGroup) {
        if (canHide) {
          add(
            ActionItem(R.drawable.ic_circle_x_24_tinted, context.getString(R.string.StoriesLandingItem__hide_story)) {
              callbacks.onHide()
            }
          )
        } else {
          // TODO [stories] -- Final icon
          add(
            ActionItem(R.drawable.ic_check_circle_24, context.getString(R.string.StoriesLandingItem__unhide_story)) {
              callbacks.onUnhide()
            }
          )
        }
      }

      if (isFromSelf) {
        add(
          ActionItem(R.drawable.ic_forward_24_tinted, context.getString(R.string.StoriesLandingItem__forward)) {
            callbacks.onForward()
          }
        )
        add(
          ActionItem(R.drawable.ic_share_24_tinted, context.getString(R.string.StoriesLandingItem__share)) {
            callbacks.onShare()
          }
        )
        add(
          ActionItem(R.drawable.ic_delete_24_tinted, context.getString(R.string.delete)) {
            callbacks.onDelete()
          }
        )
        add(
          ActionItem(R.drawable.ic_download_24_tinted, context.getString(R.string.save)) {
            callbacks.onSave()
          }
        )
      }

      if (isToGroup || !isFromSelf) {
        add(
          ActionItem(R.drawable.ic_open_24_tinted, context.getString(R.string.StoriesLandingItem__go_to_chat)) {
            callbacks.onGoToChat()
          }
        )
      }
    }

    SignalContextMenu.Builder(anchorView, rootView)
      .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
      .onDismiss {
        callbacks.onDismissed()
      }
      .show(actions)
  }

  private interface Callbacks {
    fun onHide()
    fun onUnhide()
    fun onForward()
    fun onShare()
    fun onGoToChat()
    fun onDismissed()
    fun onSave()
    fun onDelete()
  }
}
