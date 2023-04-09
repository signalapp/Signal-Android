package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import kotlinx.parcelize.Parcelize
import org.signal.core.util.StreamUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.color.ViewColorSet
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.mutiselect.Multiselect
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.hasSharedContact
import java.util.Optional
import java.util.function.Consumer

/**
 * Arguments for the MultiselectForwardFragment.
 *
 * @param canSendToNonPush       Whether non-push recipients will be displayed
 * @param multiShareArgs         The items to forward. If this is an empty list, the fragment owner will be sent back a selected list of contacts.
 * @param title                  The title to display at the top of the sheet
 * @param forceDisableAddMessage Hide the add message field even if it would normally be available.
 * @param forceSelectionOnly     Force the fragment to only select recipients, never actually performing the send.
 * @param selectSingleRecipient  Only allow the selection of a single recipient.
 * @param isWrappedInBottomSheet Whether the fragment is wrapped in a bottom sheet.
 */
@Parcelize
data class MultiselectForwardFragmentArgs @JvmOverloads constructor(
  val canSendToNonPush: Boolean,
  val multiShareArgs: List<MultiShareArgs> = listOf(),
  @StringRes val title: Int = R.string.MultiselectForwardFragment__forward_to,
  val forceDisableAddMessage: Boolean = false,
  val forceSelectionOnly: Boolean = false,
  val selectSingleRecipient: Boolean = false,
  val sendButtonColors: ViewColorSet = ViewColorSet.PRIMARY,
  val storySendRequirements: Stories.MediaTransform.SendRequirements = Stories.MediaTransform.SendRequirements.CAN_NOT_SEND,
  val isSearchEnabled: Boolean = true,
  val isViewOnce: Boolean = false,
  val isWrappedInBottomSheet: Boolean = false
) : Parcelable {

  fun withSendButtonTint(@ColorInt sendButtonTint: Int) = copy(sendButtonColors = ViewColorSet.forCustomColor(sendButtonTint))

  companion object {
    @JvmStatic
    fun create(context: Context, threadId: Long, mediaUri: Uri, mediaType: String, consumer: Consumer<MultiselectForwardFragmentArgs>) {
      SignalExecutors.BOUNDED.execute {
        val mediaSize = MediaUtil.getMediaSize(context, mediaUri)
        val isMmsSupported = Multiselect.isMmsSupported(context, mediaUri, mediaType, mediaSize)
        val multiShareArgs = MultiShareArgs.Builder(setOf())
          .withDataUri(mediaUri)
          .withDataType(mediaType)
          .build()

        val sendButtonColors: ViewColorSet? = threadId.takeIf { it > 0 }
          ?.let { SignalDatabase.threads.getRecipientForThreadId(it) }
          ?.chatColors
          ?.asSingleColor()
          ?.let { ViewColorSet.forCustomColor(it) }

        ThreadUtil.runOnMain {
          consumer.accept(
            MultiselectForwardFragmentArgs(
              isMmsSupported,
              listOf(multiShareArgs),
              storySendRequirements = Stories.MediaTransform.SendRequirements.CAN_NOT_SEND,
              sendButtonColors = sendButtonColors ?: ViewColorSet.PRIMARY
            )
          )
        }
      }
    }

    @JvmStatic
    fun create(context: Context, selectedParts: Set<MultiselectPart>, consumer: Consumer<MultiselectForwardFragmentArgs>) {
      SignalExecutors.BOUNDED.execute {
        val conversationMessages: Set<ConversationMessage> = selectedParts
          .map { it.conversationMessage }
          .toSet()

        if (conversationMessages.any { it.messageRecord.isViewOnce }) {
          throw AssertionError("Cannot forward view once media")
        }

        val canSendToNonPush: Boolean = selectedParts.all { Multiselect.canSendToNonPush(context, it) }
        val multiShareArgs: List<MultiShareArgs> = conversationMessages.map { buildMultiShareArgs(context, it, selectedParts) }

        ThreadUtil.runOnMain {
          consumer.accept(
            MultiselectForwardFragmentArgs(
              canSendToNonPush,
              multiShareArgs,
              storySendRequirements = Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
            )
          )
        }
      }
    }

    @WorkerThread
    private fun buildMultiShareArgs(context: Context, conversationMessage: ConversationMessage, selectedParts: Set<MultiselectPart>): MultiShareArgs {
      val builder = MultiShareArgs.Builder(setOf())
        .withMentions(conversationMessage.mentions)
        .withTimestamp(conversationMessage.messageRecord.timestamp)
        .withExpiration(conversationMessage.messageRecord.expireStarted + conversationMessage.messageRecord.expiresIn)
        .withBodyRanges(conversationMessage.messageRecord.messageRanges)

      if (conversationMessage.multiselectCollection.isTextSelected(selectedParts)) {
        val mediaMessage: MmsMessageRecord? = conversationMessage.messageRecord as? MmsMessageRecord
        val textSlideUri = mediaMessage?.slideDeck?.textSlide?.uri
        if (textSlideUri != null) {
          PartAuthority.getAttachmentStream(context, textSlideUri).use {
            val body = StreamUtil.readFullyAsString(it)
            val msg = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, mediaMessage, body)
            builder.withDraftText(msg.getDisplayBody(context).toString())
          }
        } else {
          builder.withDraftText(conversationMessage.getDisplayBody(context).toString())
        }

        val linkPreview = mediaMessage?.linkPreviews?.firstOrNull()
        builder.withLinkPreview(linkPreview)
        builder.asTextStory(mediaMessage?.storyType?.isTextStory ?: false)
      }

      if (conversationMessage.messageRecord.hasSharedContact() && conversationMessage.multiselectCollection.isMediaSelected(selectedParts)) {
        builder.withSharedContacts((conversationMessage.messageRecord as MmsMessageRecord).sharedContacts)
      }

      if (conversationMessage.messageRecord.isMms && conversationMessage.multiselectCollection.isMediaSelected(selectedParts)) {
        val mediaMessage: MmsMessageRecord = conversationMessage.messageRecord as MmsMessageRecord
        val isAlbum = mediaMessage.containsMediaSlide() &&
          mediaMessage.slideDeck.slides.size > 1 &&
          mediaMessage.slideDeck.audioSlide == null &&
          mediaMessage.slideDeck.documentSlide == null &&
          mediaMessage.slideDeck.stickerSlide == null

        if (isAlbum) {
          val mediaList: ArrayList<Media> = ArrayList(mediaMessage.slideDeck.slides.size)
          val attachments = mediaMessage.slideDeck.slides
            .filter { s -> s.hasImage() || s.hasVideo() }
            .map { it.asAttachment() }
            .toList()

          attachments.forEach { attachment ->
            val media = attachment.toMedia()
            if (media != null) {
              mediaList.add(media)
            }
          }

          if (mediaList.isNotEmpty()) {
            builder.withMedia(mediaList)
          }
        } else if (mediaMessage.containsMediaSlide()) {
          builder.withMedia(listOf())

          if (mediaMessage.slideDeck.stickerSlide != null) {
            builder.withDataUri(mediaMessage.slideDeck.stickerSlide?.asAttachment()?.uri)
            builder.withStickerLocator(mediaMessage.slideDeck.stickerSlide?.asAttachment()?.sticker)
            builder.withDataType(mediaMessage.slideDeck.stickerSlide?.asAttachment()?.contentType)
          }

          val firstSlide = mediaMessage.slideDeck.slides[0]
          val media = firstSlide.asAttachment().toMedia()

          if (media != null) {
            builder.asBorderless(media.isBorderless)
            builder.withMedia(listOf(media))
          }
        }
      }

      return builder.build()
    }

    private fun Attachment.toMedia(): Media? {
      val uri = this.uri ?: return null

      return Media(
        uri,
        contentType,
        System.currentTimeMillis(),
        width,
        height,
        size,
        0,
        isBorderless,
        isVideoGif,
        Optional.empty(),
        Optional.ofNullable(caption),
        Optional.of(transformProperties)
      )
    }
  }
}
