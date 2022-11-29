package org.thoughtcrime.securesms.components.settings.app.internal

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.addStyle
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.emoji.EmojiFiles
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.CreateReleaseChannelJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel

class InternalSettingsRepository(context: Context) {

  private val context = context.applicationContext

  fun getEmojiVersionInfo(consumer: (EmojiFiles.Version?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(EmojiFiles.Version.readVersion(context))
    }
  }

  fun addSampleReleaseNote() {
    SignalExecutors.UNBOUNDED.execute {
      ApplicationDependencies.getJobManager().runSynchronously(CreateReleaseChannelJob.create(), 5000)

      val title = "Release Note Title"
      val bodyText = "Release note body. Aren't I awesome?"
      val body = "$title\n\n$bodyText"
      val bodyRangeList = BodyRangeList.newBuilder()
        .addStyle(BodyRangeList.BodyRange.Style.BOLD, 0, title.length)

      val recipientId = SignalStore.releaseChannelValues().releaseChannelRecipientId!!
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

      val insertResult: MessageTable.InsertResult? = ReleaseChannel.insertReleaseChannelMessage(
        recipientId = recipientId,
        body = body,
        threadId = threadId,
        messageRanges = bodyRangeList.build(),
        image = "/static/release-notes/signal.png",
        imageWidth = 1800,
        imageHeight = 720
      )

      SignalDatabase.sms.insertBoostRequestMessage(recipientId, threadId)

      if (insertResult != null) {
        SignalDatabase.attachments.getAttachmentsForMessage(insertResult.messageId)
          .forEach { ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(insertResult.messageId, it.attachmentId, false)) }

        ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      }
    }
  }
}
