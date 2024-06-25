package org.thoughtcrime.securesms.messages

import android.graphics.Color
import org.signal.core.util.Base64
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.MessageTable.InsertResult
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.warn
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointer
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.TextAttachment

object StoryMessageProcessor {

  fun process(envelope: Envelope, content: Content, metadata: EnvelopeMetadata, senderRecipient: Recipient, threadRecipient: Recipient) {
    val storyMessage = content.storyMessage!!

    log(envelope.timestamp!!, "Story message.")

    if (threadRecipient.isInactiveGroup) {
      warn(envelope.timestamp!!, "Dropping a group story from a group we're no longer in.")
      return
    }

    if (threadRecipient.isGroup && !SignalDatabase.groups.isCurrentMember(threadRecipient.requireGroupId().requirePush(), senderRecipient.id)) {
      warn(envelope.timestamp!!, "Dropping a group story from a user who's no longer a member.")
      return
    }

    if (!threadRecipient.isGroup && !(senderRecipient.isProfileSharing || senderRecipient.isSystemContact)) {
      warn(envelope.timestamp!!, "Dropping story from an untrusted source.")
      return
    }

    val insertResult: InsertResult?

    SignalDatabase.messages.beginTransaction()

    try {
      val storyType: StoryType = if (storyMessage.allowsReplies == true) {
        StoryType.withReplies(isTextStory = storyMessage.textAttachment != null)
      } else {
        StoryType.withoutReplies(isTextStory = storyMessage.textAttachment != null)
      }

      val mediaMessage = IncomingMessage(
        type = MessageType.NORMAL,
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = System.currentTimeMillis(),
        storyType = storyType,
        isUnidentified = metadata.sealedSender,
        body = serializeTextAttachment(storyMessage),
        groupId = storyMessage.group?.groupId,
        attachments = listOfNotNull(storyMessage.fileAttachment?.toPointer()),
        linkPreviews = DataMessageProcessor.getLinkPreviews(
          previews = listOfNotNull(storyMessage.textAttachment?.preview),
          body = "",
          isStoryEmbed = true
        ),
        serverGuid = envelope.serverGuid,
        messageRanges = storyMessage.bodyRanges.filter { it.mentionAci == null }.toBodyRangeList()
      )

      insertResult = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
      if (insertResult != null) {
        SignalDatabase.messages.setTransactionSuccessful()
      }
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.messages.endTransaction()
    }

    if (insertResult != null) {
      Stories.enqueueNextStoriesForDownload(threadRecipient.id, false, RemoteConfig.storiesAutoDownloadMaximum)
      AppDependencies.expireStoriesManager.scheduleIfNecessary()
    }
  }

  fun serializeTextAttachment(story: StoryMessage): String? {
    val textAttachment = story.textAttachment ?: return null
    val builder = StoryTextPost.Builder()

    if (textAttachment.text != null) {
      builder.body = textAttachment.text!!
    }

    when (textAttachment.textStyle) {
      TextAttachment.Style.DEFAULT -> builder.style = StoryTextPost.Style.DEFAULT
      TextAttachment.Style.REGULAR -> builder.style = StoryTextPost.Style.REGULAR
      TextAttachment.Style.BOLD -> builder.style = StoryTextPost.Style.BOLD
      TextAttachment.Style.SERIF -> builder.style = StoryTextPost.Style.SERIF
      TextAttachment.Style.SCRIPT -> builder.style = StoryTextPost.Style.SCRIPT
      TextAttachment.Style.CONDENSED -> builder.style = StoryTextPost.Style.CONDENSED
      null -> Unit
    }

    if (textAttachment.textBackgroundColor != null) {
      builder.textBackgroundColor = textAttachment.textBackgroundColor!!
    }

    if (textAttachment.textForegroundColor != null) {
      builder.textForegroundColor = textAttachment.textForegroundColor!!
    }

    val chatColorBuilder = ChatColor.Builder()

    if (textAttachment.color != null) {
      chatColorBuilder.singleColor(ChatColor.SingleColor.Builder().color(textAttachment.color!!).build())
    } else if (textAttachment.gradient != null) {
      val gradient = textAttachment.gradient!!
      val linearGradientBuilder = ChatColor.LinearGradient.Builder()
      linearGradientBuilder.rotation = (gradient.angle ?: 0).toFloat()

      if (gradient.positions.size > 1 && gradient.colors.size == gradient.positions.size) {
        val positions = ArrayList(gradient.positions)
        positions[0] = 0f
        positions[positions.size - 1] = 1f
        linearGradientBuilder.colors(ArrayList(gradient.colors))
        linearGradientBuilder.positions(positions)
      } else if (gradient.colors.isNotEmpty()) {
        warn("Incoming text story has color / position mismatch. Defaulting to start and end colors.")
        linearGradientBuilder.colors(listOf(gradient.colors[0], gradient.colors[gradient.colors.size - 1]))
        linearGradientBuilder.positions(listOf(0f, 1f))
      } else if (gradient.startColor != null && gradient.endColor != null) {
        warn("Incoming text story is using deprecated fields for the gradient. Building a two color gradient with them.")
        linearGradientBuilder.colors(listOf(gradient.startColor!!, gradient.endColor!!))
        linearGradientBuilder.positions(listOf(0f, 1f))
      } else {
        warn("Incoming text story did not have a valid linear gradient.")
        linearGradientBuilder.colors(listOf(Color.BLACK, Color.BLACK))
        linearGradientBuilder.positions(listOf(0f, 1f))
      }
      chatColorBuilder.linearGradient(linearGradientBuilder.build())
    }
    builder.background(chatColorBuilder.build())

    return Base64.encodeWithPadding(builder.build().encode())
  }
}
