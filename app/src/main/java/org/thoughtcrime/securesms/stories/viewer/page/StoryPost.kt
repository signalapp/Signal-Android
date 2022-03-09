package org.thoughtcrime.securesms.stories.viewer.page

import android.net.Uri
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * Each story is made up of a collection of posts
 */
class StoryPost(
  val id: Long,
  val sender: Recipient,
  val group: Recipient?,
  val distributionList: Recipient?,
  val viewCount: Int,
  val replyCount: Int,
  val dateInMilliseconds: Long,
  val content: Content,
  val conversationMessage: ConversationMessage,
  val allowsReplies: Boolean
) {
  sealed class Content(val uri: Uri?) {
    class AttachmentContent(val attachment: Attachment) : Content(attachment.uri) {
      override val transferState: Int = attachment.transferState

      override fun isVideo(): Boolean = MediaUtil.isVideo(attachment)
    }
    class TextContent(uri: Uri, val recordId: Long) : Content(uri) {
      override val transferState: Int = AttachmentDatabase.TRANSFER_PROGRESS_DONE

      override fun isVideo(): Boolean = false
    }

    abstract val transferState: Int

    abstract fun isVideo(): Boolean
  }
}
