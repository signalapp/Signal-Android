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
import com.bumptech.glide.load.Options
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.stories.landing.StoriesLandingItem
import org.thoughtcrime.securesms.stories.viewer.page.StoryPost
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageState
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import java.io.ByteArrayInputStream

object StoryContextMenu {

  private val TAG = Log.tag(StoryContextMenu::class.java)

  fun delete(context: Context, records: Set<MessageRecord>): Single<Boolean> {
    return DeleteDialog.show(
      context = context,
      messageRecords = records,
      title = context.getString(R.string.MyStories__delete_story),
      message = context.getString(R.string.MyStories__this_story_will_be_deleted),
      forceRemoteDelete = true
    ).map { (_, deletedThread) -> deletedThread }
  }

  fun save(context: Context, messageRecord: MessageRecord) {
    val mediaMessageRecord = messageRecord as? MediaMmsMessageRecord
    val uri: Uri? = mediaMessageRecord?.slideDeck?.firstSlide?.uri
    val contentType: String? = mediaMessageRecord?.slideDeck?.firstSlide?.contentType

    if (mediaMessageRecord?.storyType?.isTextStory == true) {
      SimpleTask.run({
        val model = StoryTextPostModel.parseFrom(messageRecord)
        val decoder = StoryTextPostModel.Decoder()
        val bitmap = decoder.decode(model, 1080, 1920, Options()).get()
        val jpeg: ByteArrayInputStream = BitmapUtil.toCompressedJpeg(bitmap)

        bitmap.recycle()

        SaveAttachmentTask.Attachment(
          BlobProvider.getInstance().forData(jpeg.readBytes()).createForSingleUseInMemory(),
          MediaUtil.IMAGE_JPEG,
          mediaMessageRecord.dateSent,
          null
        )
      }, { saveAttachment ->
        SaveAttachmentTask(context)
          .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, saveAttachment)
      })
      return
    }

    if (uri == null || contentType == null) {
      Log.w(TAG, "Unable to save story media uri: $uri contentType: $contentType")
      Toast.makeText(context, R.string.MyStories__unable_to_save, Toast.LENGTH_SHORT).show()
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
    val intent = if (messageRecord.storyType.isTextStory) {
      val textStoryBody = StoryTextPost.parseFrom(Base64.decode(messageRecord.body)).body
      val linkUrl = messageRecord.linkPreviews.firstOrNull()?.url ?: ""
      val shareText = "$textStoryBody $linkUrl".trim()

      ShareCompat.IntentBuilder(fragment.requireContext())
        .setText(shareText)
        .createChooserIntent()
    } else {
      val attachment: Attachment = messageRecord.slideDeck.firstSlide!!.asAttachment()

      ShareCompat.IntentBuilder(fragment.requireContext())
        .setStream(attachment.publicUri)
        .setType(attachment.contentType)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

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
    previewView: View,
    model: StoriesLandingItem.Model,
    onDismiss: () -> Unit
  ) {
    show(
      context = context,
      anchorView = anchorView,
      isFromSelf = model.data.primaryStory.messageRecord.isOutgoing,
      isToGroup = model.data.storyRecipient.isGroup,
      isFromReleaseChannel = model.data.storyRecipient.isReleaseNotes,
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
        override fun onInfo() = model.onInfo(model, previewView)
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
    onInfo: (StoryPost) -> Unit,
    onDismiss: () -> Unit
  ) {
    val selectedStory: StoryPost = storyViewerPageState.posts.getOrNull(storyViewerPageState.selectedPostIndex) ?: return

    show(
      context = context,
      anchorView = anchorView,
      isFromSelf = selectedStory.sender.isSelf,
      isToGroup = selectedStory.group != null,
      isFromReleaseChannel = selectedStory.sender.isReleaseNotes,
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
        override fun onInfo() = onInfo(selectedStory)
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    isFromSelf: Boolean,
    isToGroup: Boolean,
    isFromReleaseChannel: Boolean,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    canHide: Boolean,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      if (!isFromSelf || isToGroup) {
        if (canHide) {
          add(
            ActionItem(R.drawable.symbol_x_circle_24, context.getString(R.string.StoriesLandingItem__hide_story)) {
              callbacks.onHide()
            }
          )
        } else {
          add(
            ActionItem(R.drawable.symbol_check_circle_24, context.getString(R.string.StoriesLandingItem__unhide_story)) {
              callbacks.onUnhide()
            }
          )
        }
      }

      if (isFromSelf) {
        add(
          ActionItem(R.drawable.symbol_forward_24, context.getString(R.string.StoriesLandingItem__forward)) {
            callbacks.onForward()
          }
        )
        add(
          ActionItem(R.drawable.symbol_share_android_24, context.getString(R.string.StoriesLandingItem__share)) {
            callbacks.onShare()
          }
        )
        add(
          ActionItem(R.drawable.symbol_trash_24, context.getString(R.string.delete)) {
            callbacks.onDelete()
          }
        )
        add(
          ActionItem(R.drawable.symbol_save_android_24, context.getString(R.string.save)) {
            callbacks.onSave()
          }
        )
      }

      if ((isToGroup || !isFromSelf) && !isFromReleaseChannel) {
        add(
          ActionItem(R.drawable.symbol_open_20, context.getString(R.string.StoriesLandingItem__go_to_chat)) {
            callbacks.onGoToChat()
          }
        )
      }

      add(
        ActionItem(R.drawable.symbol_info_24, context.getString(R.string.StoriesLandingItem__info)) {
          callbacks.onInfo()
        }
      )
    }

    SignalContextMenu.Builder(anchorView, rootView)
      .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
      .onDismiss {
        callbacks.onDismissed()
      }
      .offsetY(DimensionUnit.DP.toPixels(12f).toInt())
      .offsetX(DimensionUnit.DP.toPixels(16f).toInt())
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
    fun onInfo()
  }
}
