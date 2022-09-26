package org.thoughtcrime.securesms.stories

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Arguments for launching the story viewer, prefilled with sensible defaults.
 */
@Parcelize
data class StoryViewerArgs(
  val recipientId: RecipientId,
  val isInHiddenStoryMode: Boolean,
  val storyId: Long = -1L,
  val storyThumbTextModel: StoryTextPostModel? = null,
  val storyThumbUri: Uri? = null,
  val storyThumbBlur: BlurHash? = null,
  val recipientIds: List<RecipientId> = emptyList(),
  val isFromNotification: Boolean = false,
  val groupReplyStartPosition: Int = -1,
  val isFromInfoContextMenuAction: Boolean = false,
  val isFromQuote: Boolean = false,
  val isFromMyStories: Boolean = false,
  val isJumpToUnviewed: Boolean = false
) : Parcelable {

  class Builder(private val recipientId: RecipientId, private val isInHiddenStoryMode: Boolean) {

    private var storyId: Long = -1L
    private var storyThumbTextModel: StoryTextPostModel? = null
    private var storyThumbUri: Uri? = null
    private var storyThumbBlur: BlurHash? = null
    private var recipientIds: List<RecipientId> = emptyList()
    private var isFromNotification: Boolean = false
    private var groupReplyStartPosition: Int = -1
    private var isFromInfoContextMenuAction: Boolean = false
    private var isFromQuote: Boolean = false

    fun withStoryId(storyId: Long): Builder {
      this.storyId = storyId
      return this
    }

    fun withStoryThumbTextModel(storyThumbTextModel: StoryTextPostModel?): Builder {
      this.storyThumbTextModel = storyThumbTextModel
      return this
    }

    fun withStoryThumbUri(storyThumbUri: Uri?): Builder {
      this.storyThumbUri = storyThumbUri
      return this
    }

    fun withStoryThumbBlur(storyThumbBlur: BlurHash?): Builder {
      this.storyThumbBlur = storyThumbBlur
      return this
    }

    fun withRecipientIds(recipientIds: List<RecipientId>): Builder {
      this.recipientIds = recipientIds
      return this
    }

    fun isFromNotification(isFromNotification: Boolean): Builder {
      this.isFromNotification = isFromNotification
      return this
    }

    fun withGroupReplyStartPosition(groupReplyStartPosition: Int): Builder {
      this.groupReplyStartPosition = groupReplyStartPosition
      return this
    }

    fun isFromQuote(isFromQuote: Boolean): Builder {
      this.isFromQuote = isFromQuote
      return this
    }

    fun build(): StoryViewerArgs {
      return StoryViewerArgs(
        recipientId = recipientId,
        isInHiddenStoryMode = isInHiddenStoryMode,
        storyId = storyId,
        storyThumbTextModel = storyThumbTextModel,
        storyThumbUri = storyThumbUri,
        storyThumbBlur = storyThumbBlur,
        recipientIds = recipientIds,
        isFromNotification = isFromNotification,
        groupReplyStartPosition = groupReplyStartPosition,
        isFromInfoContextMenuAction = isFromInfoContextMenuAction,
        isFromQuote = isFromQuote
      )
    }
  }
}
