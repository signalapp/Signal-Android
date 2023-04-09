package org.thoughtcrime.securesms.messages

import android.graphics.Color
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.MessageTable.InsertResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.warn
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointer
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

object StoryMessageProcessor {

  fun process(envelope: SignalServiceProtos.Envelope, content: SignalServiceProtos.Content, metadata: EnvelopeMetadata, senderRecipient: Recipient, threadRecipient: Recipient) {
    val storyMessage = content.storyMessage

    log(envelope.timestamp, "Story message.")

    if (threadRecipient.isInactiveGroup) {
      warn(envelope.timestamp, "Dropping a group story from a group we're no longer in.")
      return
    }

    if (threadRecipient.isGroup && !SignalDatabase.groups.isCurrentMember(threadRecipient.requireGroupId().requirePush(), senderRecipient.id)) {
      warn(envelope.timestamp, "Dropping a group story from a user who's no longer a member.")
      return
    }

    if (!threadRecipient.isGroup && !(senderRecipient.isProfileSharing || senderRecipient.isSystemContact)) {
      warn(envelope.timestamp, "Dropping story from an untrusted source.")
      return
    }

    val insertResult: InsertResult?

    SignalDatabase.messages.beginTransaction()

    try {
      val storyType: StoryType = if (storyMessage.hasAllowsReplies() && storyMessage.allowsReplies) {
        StoryType.withReplies(storyMessage.hasTextAttachment())
      } else {
        StoryType.withoutReplies(storyMessage.hasTextAttachment())
      }

      val mediaMessage = IncomingMediaMessage(
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp,
        serverTimeMillis = envelope.serverTimestamp,
        receivedTimeMillis = System.currentTimeMillis(),
        storyType = storyType,
        isUnidentified = metadata.sealedSender,
        body = serializeTextAttachment(storyMessage),
        groupId = storyMessage.group.groupId,
        attachments = if (storyMessage.hasFileAttachment()) listOfNotNull(storyMessage.fileAttachment.toPointer()) else emptyList(),
        linkPreviews = DataMessageProcessor.getLinkPreviews(
          previews = if (storyMessage.textAttachment.hasPreview()) listOf(storyMessage.textAttachment.preview) else emptyList(),
          body = "",
          isStoryEmbed = true
        ),
        serverGuid = envelope.serverGuid,
        messageRanges = storyMessage.bodyRangesList.filterNot { it.hasMentionUuid() }.toBodyRangeList()
      )

      insertResult = SignalDatabase.messages.insertSecureDecryptedMessageInbox(mediaMessage, -1).orNull()
      if (insertResult != null) {
        SignalDatabase.messages.setTransactionSuccessful()
      }
    } catch (e: MmsException) {
      throw MessageContentProcessor.StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.messages.endTransaction()
    }

    if (insertResult != null) {
      Stories.enqueueNextStoriesForDownload(threadRecipient.id, false, FeatureFlags.storiesAutoDownloadMaximum())
      ApplicationDependencies.getExpireStoriesManager().scheduleIfNecessary()
    }
  }

  fun serializeTextAttachment(story: SignalServiceProtos.StoryMessage): String? {
    if (!story.hasTextAttachment()) {
      return null
    }
    val textAttachment = story.textAttachment
    val builder = StoryTextPost.newBuilder()

    if (textAttachment.hasText()) {
      builder.body = textAttachment.text
    }

    if (textAttachment.hasTextStyle()) {
      when (textAttachment.textStyle) {
        SignalServiceProtos.TextAttachment.Style.DEFAULT -> builder.style = StoryTextPost.Style.DEFAULT
        SignalServiceProtos.TextAttachment.Style.REGULAR -> builder.style = StoryTextPost.Style.REGULAR
        SignalServiceProtos.TextAttachment.Style.BOLD -> builder.style = StoryTextPost.Style.BOLD
        SignalServiceProtos.TextAttachment.Style.SERIF -> builder.style = StoryTextPost.Style.SERIF
        SignalServiceProtos.TextAttachment.Style.SCRIPT -> builder.style = StoryTextPost.Style.SCRIPT
        SignalServiceProtos.TextAttachment.Style.CONDENSED -> builder.style = StoryTextPost.Style.CONDENSED
        else -> Unit
      }
    }

    if (textAttachment.hasTextBackgroundColor()) {
      builder.textBackgroundColor = textAttachment.textBackgroundColor
    }

    if (textAttachment.hasTextForegroundColor()) {
      builder.textForegroundColor = textAttachment.textForegroundColor
    }

    val chatColorBuilder = ChatColor.newBuilder()

    if (textAttachment.hasColor()) {
      chatColorBuilder.setSingleColor(ChatColor.SingleColor.newBuilder().setColor(textAttachment.color))
    } else if (textAttachment.hasGradient()) {
      val gradient = textAttachment.gradient
      val linearGradientBuilder = ChatColor.LinearGradient.newBuilder()
      linearGradientBuilder.rotation = gradient.angle.toFloat()

      if (gradient.positionsList.size > 1 && gradient.colorsList.size == gradient.positionsList.size) {
        val positions = ArrayList(gradient.positionsList)
        positions[0] = 0f
        positions[positions.size - 1] = 1f
        linearGradientBuilder.addAllColors(ArrayList(gradient.colorsList))
        linearGradientBuilder.addAllPositions(positions)
      } else if (gradient.colorsList.isNotEmpty()) {
        warn("Incoming text story has color / position mismatch. Defaulting to start and end colors.")
        linearGradientBuilder.addColors(gradient.colorsList[0])
        linearGradientBuilder.addColors(gradient.colorsList[gradient.colorsList.size - 1])
        linearGradientBuilder.addAllPositions(listOf(0f, 1f))
      } else {
        warn("Incoming text story did not have a valid linear gradient.")
        linearGradientBuilder.addAllColors(listOf(Color.BLACK, Color.BLACK))
        linearGradientBuilder.addAllPositions(listOf(0f, 1f))
      }
      chatColorBuilder.setLinearGradient(linearGradientBuilder)
    }
    builder.setBackground(chatColorBuilder)

    return Base64.encodeBytes(builder.build().toByteArray())
  }
}
