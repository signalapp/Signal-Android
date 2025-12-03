package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import org.signal.core.models.ServiceId
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupNotAMemberException
import org.thoughtcrime.securesms.messages.GroupSendUtil
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.transport.UndeliverableMessageException
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Companion.newBuilder
import java.io.IOException
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Functions used when pinning/unpinning messages
 */
object PinSendUtil {

  private val PIN_TERMINATE_TIMEOUT = 7000.milliseconds

  @Throws(IOException::class, GroupNotAMemberException::class, UndeliverableMessageException::class)
  fun sendPinMessage(applicationContext: Context, threadRecipient: Recipient, message: OutgoingMessage, destinations: List<Recipient>): List<SendMessageResult?> {
    val builder = newBuilder()
    val groupId = if (threadRecipient.isPushV2Group) threadRecipient.requireGroupId().requireV2() else null

    if (groupId != null) {
      val groupRecord: GroupRecord? = SignalDatabase.groups.getGroup(groupId).getOrNull()
      if (groupRecord != null && groupRecord.attributesAccessControl == GroupAccessControl.ONLY_ADMINS && !groupRecord.isAdmin(Recipient.self())) {
        throw UndeliverableMessageException("Non-admins cannot pin messages!")
      }
      GroupUtil.setDataMessageGroupContext(AppDependencies.application, builder, groupId)
    }

    val sentTime = System.currentTimeMillis()
    val message = builder
      .withTimestamp(sentTime)
      .withExpiration((message.expiresIn / 1000).toInt())
      .withProfileKey(ProfileKeyUtil.getSelfProfileKey().serialize())
      .withPinnedMessage(
        SignalServiceDataMessage.PinnedMessage(
          targetAuthor = ServiceId.parseOrThrow(message.messageExtras!!.pinnedMessage!!.targetAuthorAci),
          targetSentTimestamp = message.messageExtras.pinnedMessage.targetTimestamp,
          pinDurationInSeconds = message.messageExtras.pinnedMessage.pinDurationInSeconds.takeIf { it != MessageTable.PIN_FOREVER }?.toInt(),
          forever = (message.messageExtras.pinnedMessage.pinDurationInSeconds == MessageTable.PIN_FOREVER).takeIf { it }
        )
      )
      .build()

    return GroupSendUtil.sendUnresendableDataMessage(
      applicationContext,
      groupId,
      destinations,
      false,
      ContentHint.DEFAULT,
      message,
      false
    ) { System.currentTimeMillis() - sentTime > PIN_TERMINATE_TIMEOUT.inWholeMilliseconds }
  }

  @Throws(IOException::class, GroupNotAMemberException::class, UndeliverableMessageException::class)
  fun sendUnpinMessage(applicationContext: Context, threadRecipient: Recipient, targetAuthor: ServiceId, targetSentTimestamp: Long, destinations: List<Recipient>): List<SendMessageResult?> {
    val builder = newBuilder()
    val groupId = if (threadRecipient.isPushV2Group) threadRecipient.requireGroupId().requireV2() else null
    if (groupId != null) {
      val groupRecord: GroupRecord? = SignalDatabase.groups.getGroup(groupId).getOrNull()
      if (groupRecord != null && groupRecord.attributesAccessControl == GroupAccessControl.ONLY_ADMINS && !groupRecord.isAdmin(Recipient.self())) {
        throw UndeliverableMessageException("Non-admins cannot pin messages!")
      }

      GroupUtil.setDataMessageGroupContext(AppDependencies.application, builder, groupId)
    }

    val sentTime = System.currentTimeMillis()
    val message = builder
      .withTimestamp(sentTime)
      .withProfileKey(ProfileKeyUtil.getSelfProfileKey().serialize())
      .withUnpinnedMessage(
        SignalServiceDataMessage.UnpinnedMessage(
          targetAuthor = targetAuthor,
          targetSentTimestamp = targetSentTimestamp
        )
      )
      .build()

    return GroupSendUtil.sendUnresendableDataMessage(
      applicationContext,
      groupId,
      destinations,
      false,
      ContentHint.DEFAULT,
      message,
      false
    ) { System.currentTimeMillis() - sentTime > PIN_TERMINATE_TIMEOUT.inWholeMilliseconds }
  }
}
