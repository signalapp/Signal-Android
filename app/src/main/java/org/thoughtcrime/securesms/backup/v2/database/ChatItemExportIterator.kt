/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONException
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository.getMediaName
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.ContactAttachment
import org.thoughtcrime.securesms.backup.v2.proto.ContactMessage
import org.thoughtcrime.securesms.backup.v2.proto.ExpirationTimerChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.proto.GroupCall
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCall
import org.thoughtcrime.securesms.backup.v2.proto.LearnedProfileChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.MessageAttachment
import org.thoughtcrime.securesms.backup.v2.proto.PaymentNotification
import org.thoughtcrime.securesms.backup.v2.proto.ProfileChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.RemoteDeletedMessage
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SessionSwitchoverChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.Sticker
import org.thoughtcrime.securesms.backup.v2.proto.StickerMessage
import org.thoughtcrime.securesms.backup.v2.proto.Text
import org.thoughtcrime.securesms.backup.v2.proto.ThreadMergeChatUpdate
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.PaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.calls
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.payments.FailureReason
import org.thoughtcrime.securesms.payments.State
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.Closeable
import java.io.IOException
import java.util.HashMap
import java.util.LinkedList
import java.util.Queue
import kotlin.jvm.optionals.getOrNull
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange as BackupBodyRange
import org.thoughtcrime.securesms.backup.v2.proto.GiftBadge as BackupGiftBadge

/**
 * An iterator for chat items with a clever performance twist: rather than do the extra queries one at a time (for reactions,
 * attachments, etc), this will populate items in batches, doing bulk lookups to improve throughput. We keep these in a buffer
 * and only do more queries when the buffer is empty.
 *
 * All of this complexity is hidden from the user -- they just get a normal iterator interface.
 */
class ChatItemExportIterator(private val cursor: Cursor, private val batchSize: Int, private val archiveMedia: Boolean) : Iterator<ChatItem?>, Closeable {

  companion object {
    private val TAG = Log.tag(ChatItemExportIterator::class.java)

    const val COLUMN_BASE_TYPE = "base_type"
  }

  /**
   * A queue of already-parsed ChatItems. Processing in batches means that we read ahead in the cursor and put
   * the pending items here.
   */
  private val buffer: Queue<ChatItem> = LinkedList()

  private val revisionMap: HashMap<Long, ArrayList<ChatItem>> = HashMap()

  override fun hasNext(): Boolean {
    return buffer.isNotEmpty() || (cursor.count > 0 && !cursor.isLast && !cursor.isAfterLast)
  }

  override fun next(): ChatItem? {
    if (buffer.isNotEmpty()) {
      return buffer.remove()
    }

    val records: LinkedHashMap<Long, BackupMessageRecord> = linkedMapOf()

    for (i in 0 until batchSize) {
      if (cursor.moveToNext()) {
        val record = cursor.toBackupMessageRecord()
        records[record.id] = record
      } else {
        break
      }
    }

    val reactionsById: Map<Long, List<ReactionRecord>> = SignalDatabase.reactions.getReactionsForMessages(records.keys)
    val mentionsById: Map<Long, List<Mention>> = SignalDatabase.mentions.getMentionsForMessages(records.keys)
    val attachmentsById: Map<Long, List<DatabaseAttachment>> = SignalDatabase.attachments.getAttachmentsForMessages(records.keys)
    val groupReceiptsById: Map<Long, List<GroupReceiptTable.GroupReceiptInfo>> = SignalDatabase.groupReceipts.getGroupReceiptInfoForMessages(records.keys)

    for ((id, record) in records) {
      val builder = record.toBasicChatItemBuilder(groupReceiptsById[id])

      when {
        record.remoteDeleted -> {
          builder.remoteDeletedMessage = RemoteDeletedMessage()
        }
        MessageTypes.isJoinedType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.JOINED_SIGNAL)
        }
        MessageTypes.isIdentityUpdate(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_UPDATE)
        }
        MessageTypes.isIdentityVerified(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_VERIFIED)
        }
        MessageTypes.isIdentityDefault(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_DEFAULT)
        }
        MessageTypes.isChangeNumber(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.CHANGE_NUMBER)
        }
        MessageTypes.isReleaseChannelDonationRequest(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.RELEASE_CHANNEL_DONATION_REQUEST)
        }
        MessageTypes.isEndSessionType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.END_SESSION)
        }
        MessageTypes.isChatSessionRefresh(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.CHAT_SESSION_REFRESH)
        }
        MessageTypes.isBadDecryptType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.BAD_DECRYPT)
        }
        MessageTypes.isPaymentsActivated(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.PAYMENTS_ACTIVATED)
        }
        MessageTypes.isPaymentsRequestToActivate(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST)
        }
        MessageTypes.isUnsupportedMessageType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.UNSUPPORTED_PROTOCOL_MESSAGE)
        }
        MessageTypes.isReportedSpam(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.REPORTED_SPAM)
        }
        MessageTypes.isExpirationTimerUpdate(record.type) -> {
          builder.updateMessage = ChatUpdateMessage(expirationTimerChange = ExpirationTimerChatUpdate(record.expiresIn.toInt()))
          builder.expiresInMs = 0
        }
        MessageTypes.isProfileChange(record.type) -> {
          builder.updateMessage = record.toProfileChangeUpdate()
        }
        MessageTypes.isSessionSwitchoverType(record.type) -> {
          builder.updateMessage = record.toSessionSwitchoverUpdate()
        }
        MessageTypes.isThreadMergeType(record.type) -> {
          builder.updateMessage = record.toThreadMergeUpdate()
        }
        MessageTypes.isGroupV2(record.type) && MessageTypes.isGroupUpdate(record.type) -> {
          builder.updateMessage = record.toGroupUpdate()
        }
        MessageTypes.isCallLog(record.type) -> {
          builder.updateMessage = record.toCallUpdate()
        }
        MessageTypes.isPaymentsNotification(record.type) -> {
          builder.paymentNotification = record.toPaymentNotificationUpdate()
        }
        MessageTypes.isGiftBadge(record.type) -> {
          builder.giftBadge = record.toGiftBadgeUpdate()
        }
        !record.sharedContacts.isNullOrEmpty() -> {
          builder.contactMessage = record.toContactMessage(reactionsById[id], attachmentsById[id])
        }
        else -> {
          if (record.body == null && !attachmentsById.containsKey(record.id)) {
            Log.w(TAG, "Record with ID ${record.id} missing a body and doesn't have attachments. Skipping.")
            continue
          }

          val attachments = attachmentsById[record.id]
          val sticker = attachments?.firstOrNull { dbAttachment ->
            dbAttachment.isSticker
          }

          if (sticker?.stickerLocator != null) {
            builder.stickerMessage = sticker.toStickerMessage(reactionsById[id])
          } else {
            builder.standardMessage = record.toStandardMessage(reactionsById[id], mentions = mentionsById[id], attachments = attachmentsById[record.id])
          }
        }
      }

      if (record.latestRevisionId == null) {
        val previousEdits = revisionMap.remove(record.id)
        if (previousEdits != null) {
          builder.revisions = previousEdits
        }
        buffer += builder.build()
      } else {
        var previousEdits = revisionMap[record.latestRevisionId]
        if (previousEdits == null) {
          previousEdits = ArrayList()
          revisionMap[record.latestRevisionId] = previousEdits
        }
        previousEdits += builder.build()
      }
    }

    return if (buffer.isNotEmpty()) {
      buffer.remove()
    } else {
      null
    }
  }

  override fun close() {
    cursor.close()
  }

  private fun simpleUpdate(type: SimpleChatUpdate.Type): ChatUpdateMessage {
    return ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = type))
  }

  private fun BackupMessageRecord.toBasicChatItemBuilder(groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?): ChatItem.Builder {
    val record = this

    return ChatItem.Builder().apply {
      chatId = record.threadId
      authorId = record.fromRecipientId
      dateSent = record.dateSent
      expireStartDate = if (record.expireStarted > 0) record.expireStarted else 0
      expiresInMs = if (record.expiresIn > 0) record.expiresIn else 0
      revisions = emptyList()
      sms = record.type.isSmsType()
      if (record.type.isDirectionlessType()) {
        directionless = ChatItem.DirectionlessMessageDetails()
      } else if (MessageTypes.isOutgoingMessageType(record.type)) {
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = record.toBackupSendStatus(groupReceipts)
        )
      } else {
        incoming = ChatItem.IncomingMessageDetails(
          dateServerSent = record.dateServer,
          dateReceived = record.dateReceived,
          read = record.read,
          sealedSender = record.sealedSender
        )
      }
    }
  }

  private fun BackupMessageRecord.toProfileChangeUpdate(): ChatUpdateMessage? {
    val profileChangeDetails = if (this.messageExtras != null) {
      this.messageExtras.profileChangeDetails
    } else {
      Base64.decodeOrNull(this.body)?.let { ProfileChangeDetails.ADAPTER.decode(it) }
    }

    return if (profileChangeDetails?.profileNameChange != null) {
      ChatUpdateMessage(profileChange = ProfileChangeChatUpdate(previousName = profileChangeDetails.profileNameChange.previous, newName = profileChangeDetails.profileNameChange.newValue))
    } else if (profileChangeDetails?.learnedProfileName != null) {
      ChatUpdateMessage(learnedProfileChange = LearnedProfileChatUpdate(e164 = profileChangeDetails.learnedProfileName.e164?.e164ToLong(), username = profileChangeDetails.learnedProfileName.username))
    } else {
      null
    }
  }

  private fun BackupMessageRecord.toSessionSwitchoverUpdate(): ChatUpdateMessage {
    if (this.body == null) {
      return ChatUpdateMessage(sessionSwitchover = SessionSwitchoverChatUpdate())
    }

    return ChatUpdateMessage(
      sessionSwitchover = try {
        val event = SessionSwitchoverEvent.ADAPTER.decode(Base64.decodeOrThrow(this.body))
        SessionSwitchoverChatUpdate(event.e164.e164ToLong()!!)
      } catch (e: IOException) {
        SessionSwitchoverChatUpdate()
      }
    )
  }

  private fun BackupMessageRecord.toThreadMergeUpdate(): ChatUpdateMessage {
    if (this.body == null) {
      return ChatUpdateMessage(threadMerge = ThreadMergeChatUpdate())
    }

    return ChatUpdateMessage(
      threadMerge = try {
        val event = ThreadMergeEvent.ADAPTER.decode(Base64.decodeOrThrow(this.body))
        ThreadMergeChatUpdate(event.previousE164.e164ToLong()!!)
      } catch (e: IOException) {
        ThreadMergeChatUpdate()
      }
    )
  }

  private fun BackupMessageRecord.toGroupUpdate(): ChatUpdateMessage? {
    val groupChange = this.messageExtras?.gv2UpdateDescription?.groupChangeUpdate
    return if (groupChange != null) {
      ChatUpdateMessage(
        groupChange = groupChange
      )
    } else if (this.body != null) {
      try {
        val decoded: ByteArray = Base64.decode(this.body)
        val context = DecryptedGroupV2Context.ADAPTER.decode(decoded)
        ChatUpdateMessage(
          groupChange = GroupsV2UpdateMessageConverter.translateDecryptedChange(selfIds = SignalStore.account.getServiceIds(), context)
        )
      } catch (e: IOException) {
        null
      }
    } else {
      null
    }
  }

  private fun BackupMessageRecord.toCallUpdate(): ChatUpdateMessage? {
    val call = calls.getCallByMessageId(this.id)

    return if (call != null) {
      call.toCallUpdate()
    } else {
      when {
        MessageTypes.isMissedAudioCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.AUDIO_CALL,
              state = IndividualCall.State.MISSED,
              direction = IndividualCall.Direction.INCOMING
            )
          )
        }
        MessageTypes.isMissedVideoCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.VIDEO_CALL,
              state = IndividualCall.State.MISSED,
              direction = IndividualCall.Direction.INCOMING
            )
          )
        }
        MessageTypes.isIncomingAudioCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.AUDIO_CALL,
              state = IndividualCall.State.ACCEPTED,
              direction = IndividualCall.Direction.INCOMING
            )
          )
        }
        MessageTypes.isIncomingVideoCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.VIDEO_CALL,
              state = IndividualCall.State.ACCEPTED,
              direction = IndividualCall.Direction.INCOMING
            )
          )
        }
        MessageTypes.isOutgoingAudioCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.AUDIO_CALL,
              state = IndividualCall.State.ACCEPTED,
              direction = IndividualCall.Direction.OUTGOING
            )
          )
        }
        MessageTypes.isOutgoingVideoCall(this.type) -> {
          ChatUpdateMessage(
            individualCall = IndividualCall(
              type = IndividualCall.Type.VIDEO_CALL,
              state = IndividualCall.State.ACCEPTED,
              direction = IndividualCall.Direction.OUTGOING
            )
          )
        }
        MessageTypes.isGroupCall(this.type) -> {
          try {
            val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(this.body)
            ChatUpdateMessage(
              groupCall = GroupCall(
                state = GroupCall.State.GENERIC,
                startedCallRecipientId = recipients.getByAci(ACI.from(UuidUtil.parseOrThrow(groupCallUpdateDetails.startedCallUuid))).getOrNull()?.toLong(),
                startedCallTimestamp = groupCallUpdateDetails.startedCallTimestamp,
                endedCallTimestamp = groupCallUpdateDetails.endedCallTimestamp
              )
            )
          } catch (exception: IOException) {
            null
          }
        }
        else -> {
          null
        }
      }
    }
  }

  private fun CallTable.Call.toCallUpdate(): ChatUpdateMessage? {
    return if (this.type == CallTable.Type.GROUP_CALL) {
      ChatUpdateMessage(
        groupCall = GroupCall(
          callId = this.messageId,
          state = when (this.event) {
            CallTable.Event.MISSED -> GroupCall.State.MISSED
            CallTable.Event.ONGOING -> GroupCall.State.GENERIC
            CallTable.Event.ACCEPTED -> GroupCall.State.ACCEPTED
            CallTable.Event.NOT_ACCEPTED -> GroupCall.State.GENERIC
            CallTable.Event.MISSED_NOTIFICATION_PROFILE -> GroupCall.State.MISSED_NOTIFICATION_PROFILE
            CallTable.Event.GENERIC_GROUP_CALL -> GroupCall.State.GENERIC
            CallTable.Event.JOINED -> GroupCall.State.JOINED
            CallTable.Event.RINGING -> GroupCall.State.RINGING
            CallTable.Event.DECLINED -> GroupCall.State.DECLINED
            CallTable.Event.OUTGOING_RING -> GroupCall.State.OUTGOING_RING
            CallTable.Event.DELETE -> return null
          },
          ringerRecipientId = this.ringerRecipient?.toLong(),
          startedCallRecipientId = this.ringerRecipient?.toLong(),
          startedCallTimestamp = this.timestamp
        )
      )
    } else if (this.type != CallTable.Type.AD_HOC_CALL) {
      ChatUpdateMessage(
        individualCall = IndividualCall(
          callId = this.callId,
          type = if (this.type == CallTable.Type.VIDEO_CALL) IndividualCall.Type.VIDEO_CALL else IndividualCall.Type.AUDIO_CALL,
          direction = if (this.direction == CallTable.Direction.INCOMING) IndividualCall.Direction.INCOMING else IndividualCall.Direction.OUTGOING,
          state = when (this.event) {
            CallTable.Event.MISSED -> IndividualCall.State.MISSED
            CallTable.Event.MISSED_NOTIFICATION_PROFILE -> IndividualCall.State.MISSED_NOTIFICATION_PROFILE
            CallTable.Event.ACCEPTED -> IndividualCall.State.ACCEPTED
            CallTable.Event.NOT_ACCEPTED -> IndividualCall.State.NOT_ACCEPTED
            else -> IndividualCall.State.UNKNOWN_STATE
          },
          startedCallTimestamp = this.timestamp
        )
      )
    } else {
      null
    }
  }

  private fun BackupMessageRecord.toPaymentNotificationUpdate(): PaymentNotification {
    val paymentUuid = UuidUtil.parseOrNull(this.body)
    val payment = if (paymentUuid != null) {
      SignalDatabase.payments.getPayment(paymentUuid)
    } else {
      null
    }

    return if (payment == null) {
      PaymentNotification()
    } else {
      PaymentNotification(
        amountMob = payment.amount.serializeAmountString(),
        feeMob = payment.fee.serializeAmountString(),
        note = payment.note,
        transactionDetails = payment.getTransactionDetails()
      )
    }
  }

  private fun BackupMessageRecord.parseSharedContacts(attachments: List<DatabaseAttachment>?): List<Contact> {
    if (this.sharedContacts.isNullOrEmpty()) {
      return emptyList()
    }

    val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments?.associateBy { it.attachmentId } ?: emptyMap()

    try {
      val contacts: MutableList<Contact> = LinkedList()
      val jsonContacts = JSONArray(sharedContacts)

      for (i in 0 until jsonContacts.length()) {
        val contact: Contact = Contact.deserialize(jsonContacts.getJSONObject(i).toString())

        if (contact.avatar != null && contact.avatar!!.attachmentId != null) {
          val attachment = attachmentIdMap[contact.avatar!!.attachmentId]

          val updatedAvatar = Contact.Avatar(
            contact.avatar!!.attachmentId,
            attachment,
            contact.avatar!!.isProfile
          )

          contacts += Contact(contact, updatedAvatar)
        } else {
          contacts += contact
        }
      }

      return contacts
    } catch (e: JSONException) {
      Log.w(TAG, "Failed to parse shared contacts.", e)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse shared contacts.", e)
    }

    return emptyList()
  }

  private fun BackupMessageRecord.parseLinkPreviews(attachments: List<DatabaseAttachment>?): List<LinkPreview> {
    if (linkPreview.isNullOrEmpty()) {
      return emptyList()
    }
    val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments?.associateBy { it.attachmentId } ?: emptyMap()

    try {
      val previews: MutableList<LinkPreview> = LinkedList()
      val jsonPreviews = JSONArray(linkPreview)

      for (i in 0 until jsonPreviews.length()) {
        val preview = LinkPreview.deserialize(jsonPreviews.getJSONObject(i).toString())

        if (preview.attachmentId != null) {
          val attachment = attachmentIdMap[preview.attachmentId]

          if (attachment != null) {
            previews += LinkPreview(preview.url, preview.title, preview.description, preview.date, attachment)
          } else {
            previews += preview
          }
        } else {
          previews += preview
        }
      }

      return previews
    } catch (e: JSONException) {
      Log.w(TAG, "Failed to parse link preview", e)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse shared contacts.", e)
    }

    return emptyList()
  }

  private fun LinkPreview.toBackupLinkPreview(): org.thoughtcrime.securesms.backup.v2.proto.LinkPreview {
    return org.thoughtcrime.securesms.backup.v2.proto.LinkPreview(
      url = url,
      title = title,
      image = (thumbnail.orNull() as? DatabaseAttachment)?.toBackupAttachment()?.pointer,
      description = description,
      date = date
    )
  }

  private fun BackupMessageRecord.toContactMessage(reactionRecords: List<ReactionRecord>?, attachments: List<DatabaseAttachment>?): ContactMessage {
    val sharedContacts = parseSharedContacts(attachments)

    val contacts = sharedContacts.map {
      ContactAttachment(
        name = it.name.toBackup(),
        avatar = (it.avatar?.attachment as? DatabaseAttachment)?.toBackupAttachment()?.pointer,
        organization = it.organization,
        number = it.phoneNumbers.map { phone ->
          ContactAttachment.Phone(
            value_ = phone.number,
            type = phone.type.toBackup(),
            label = phone.label
          )
        },
        email = it.emails.map { email ->
          ContactAttachment.Email(
            value_ = email.email,
            label = email.label,
            type = email.type.toBackup()
          )
        },
        address = it.postalAddresses.map { address ->
          ContactAttachment.PostalAddress(
            type = address.type.toBackup(),
            label = address.label,
            street = address.street,
            pobox = address.poBox,
            neighborhood = address.neighborhood,
            city = address.city,
            region = address.region,
            postcode = address.postalCode,
            country = address.country
          )
        }
      )
    }
    return ContactMessage(
      contact = contacts,
      reactions = reactionRecords.toBackupReactions()
    )
  }

  private fun Contact.Name.toBackup(): ContactAttachment.Name {
    return ContactAttachment.Name(
      givenName = givenName,
      familyName = familyName,
      prefix = prefix,
      suffix = suffix,
      middleName = middleName,
      displayName = displayName
    )
  }

  private fun Contact.Phone.Type.toBackup(): ContactAttachment.Phone.Type {
    return when (this) {
      Contact.Phone.Type.HOME -> ContactAttachment.Phone.Type.HOME
      Contact.Phone.Type.MOBILE -> ContactAttachment.Phone.Type.MOBILE
      Contact.Phone.Type.WORK -> ContactAttachment.Phone.Type.WORK
      Contact.Phone.Type.CUSTOM -> ContactAttachment.Phone.Type.CUSTOM
    }
  }

  private fun Contact.Email.Type.toBackup(): ContactAttachment.Email.Type {
    return when (this) {
      Contact.Email.Type.HOME -> ContactAttachment.Email.Type.HOME
      Contact.Email.Type.MOBILE -> ContactAttachment.Email.Type.MOBILE
      Contact.Email.Type.WORK -> ContactAttachment.Email.Type.WORK
      Contact.Email.Type.CUSTOM -> ContactAttachment.Email.Type.CUSTOM
    }
  }

  private fun Contact.PostalAddress.Type.toBackup(): ContactAttachment.PostalAddress.Type {
    return when (this) {
      Contact.PostalAddress.Type.HOME -> ContactAttachment.PostalAddress.Type.HOME
      Contact.PostalAddress.Type.WORK -> ContactAttachment.PostalAddress.Type.WORK
      Contact.PostalAddress.Type.CUSTOM -> ContactAttachment.PostalAddress.Type.CUSTOM
    }
  }

  private fun BackupMessageRecord.toStandardMessage(reactionRecords: List<ReactionRecord>?, mentions: List<Mention>?, attachments: List<DatabaseAttachment>?): StandardMessage {
    val text = if (body == null) {
      null
    } else {
      Text(
        body = this.body,
        bodyRanges = (this.bodyRanges?.toBackupBodyRanges() ?: emptyList()) + (mentions?.toBackupBodyRanges() ?: emptyList())
      )
    }
    val linkPreviews = parseLinkPreviews(attachments)
    val linkPreviewAttachments = linkPreviews.mapNotNull { it.thumbnail.orElse(null) }.toSet()
    val quotedAttachments = attachments?.filter { it.quote } ?: emptyList()
    val messageAttachments = attachments
      ?.filterNot { it.quote }
      ?.filterNot { linkPreviewAttachments.contains(it) }
      ?: emptyList()
    return StandardMessage(
      quote = this.toQuote(quotedAttachments),
      text = text,
      attachments = messageAttachments.toBackupAttachments(),
      linkPreview = linkPreviews.map { it.toBackupLinkPreview() },
      longText = null,
      reactions = reactionRecords.toBackupReactions()
    )
  }

  private fun BackupMessageRecord.toQuote(attachments: List<DatabaseAttachment>? = null): Quote? {
    return if (this.quoteTargetSentTimestamp != MessageTable.QUOTE_NOT_PRESENT_ID && this.quoteAuthor > 0) {
      val type = QuoteModel.Type.fromCode(this.quoteType)
      Quote(
        targetSentTimestamp = this.quoteTargetSentTimestamp.takeIf { !this.quoteMissing && it != MessageTable.QUOTE_TARGET_MISSING_ID },
        authorId = this.quoteAuthor,
        text = this.quoteBody,
        attachments = attachments?.toBackupQuoteAttachments() ?: emptyList(),
        bodyRanges = this.quoteBodyRanges?.toBackupBodyRanges() ?: emptyList(),
        type = when (type) {
          QuoteModel.Type.NORMAL -> Quote.Type.NORMAL
          QuoteModel.Type.GIFT_BADGE -> Quote.Type.GIFTBADGE
        }
      )
    } else {
      null
    }
  }

  private fun BackupMessageRecord.toGiftBadgeUpdate(): BackupGiftBadge {
    val giftBadge = try {
      GiftBadge.ADAPTER.decode(Base64.decode(this.body ?: ""))
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode GiftBadge!")
      return BackupGiftBadge()
    }

    return BackupGiftBadge(
      receiptCredentialPresentation = giftBadge.redemptionToken,
      state = when (giftBadge.redemptionState) {
        GiftBadge.RedemptionState.REDEEMED -> BackupGiftBadge.State.REDEEMED
        GiftBadge.RedemptionState.FAILED -> BackupGiftBadge.State.FAILED
        GiftBadge.RedemptionState.PENDING -> BackupGiftBadge.State.UNOPENED
        GiftBadge.RedemptionState.STARTED -> BackupGiftBadge.State.OPENED
      }
    )
  }

  private fun DatabaseAttachment.toStickerMessage(reactions: List<ReactionRecord>?): StickerMessage {
    val stickerLocator = this.stickerLocator!!
    return StickerMessage(
      sticker = Sticker(
        packId = Hex.fromStringCondensed(stickerLocator.packId).toByteString(),
        packKey = Hex.fromStringCondensed(stickerLocator.packKey).toByteString(),
        stickerId = stickerLocator.stickerId,
        emoji = stickerLocator.emoji,
        data_ = this.toBackupAttachment().pointer
      ),
      reactions = reactions.toBackupReactions()
    )
  }

  private fun List<DatabaseAttachment>.toBackupQuoteAttachments(): List<Quote.QuotedAttachment> {
    return this.map { attachment ->
      Quote.QuotedAttachment(
        contentType = attachment.contentType,
        fileName = attachment.fileName,
        thumbnail = attachment.toBackupAttachment()
      )
    }
  }

  private fun DatabaseAttachment.toBackupAttachment(): MessageAttachment {
    val builder = FilePointer.Builder()
    builder.contentType = contentType
    builder.incrementalMac = incrementalDigest?.toByteString()
    builder.incrementalMacChunkSize = incrementalMacChunkSize
    builder.fileName = fileName
    builder.width = width
    builder.height = height
    builder.caption = caption
    builder.blurHash = blurHash?.hash

    if (remoteKey.isNullOrBlank() || remoteDigest == null || size == 0L) {
      builder.invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator()
    } else {
      if (archiveMedia) {
        builder.backupLocator = FilePointer.BackupLocator(
          mediaName = archiveMediaName ?: this.getMediaName().toString(),
          cdnNumber = if (archiveMediaName != null) archiveCdn else Cdn.CDN_3.cdnNumber, // TODO (clark): Update when new proto with optional cdn is landed
          key = Base64.decode(remoteKey).toByteString(),
          size = this.size.toInt(),
          digest = remoteDigest.toByteString()
        )
      } else {
        if (remoteLocation.isNullOrBlank()) {
          builder.invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator()
        } else {
          builder.attachmentLocator = FilePointer.AttachmentLocator(
            cdnKey = this.remoteLocation,
            cdnNumber = this.cdn.cdnNumber,
            uploadTimestamp = this.uploadTimestamp,
            key = Base64.decode(remoteKey).toByteString(),
            size = this.size.toInt(),
            digest = remoteDigest.toByteString()
          )
        }
      }
    }
    return MessageAttachment(
      pointer = builder.build(),
      wasDownloaded = this.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE || this.transferState == AttachmentTable.TRANSFER_NEEDS_RESTORE,
      flag = if (voiceNote) {
        MessageAttachment.Flag.VOICE_MESSAGE
      } else if (videoGif) {
        MessageAttachment.Flag.GIF
      } else if (borderless) {
        MessageAttachment.Flag.BORDERLESS
      } else {
        MessageAttachment.Flag.NONE
      },
      clientUuid = uuid?.let { UuidUtil.toByteString(uuid) }
    )
  }

  private fun List<DatabaseAttachment>.toBackupAttachments(): List<MessageAttachment> {
    return this.map { attachment ->
      attachment.toBackupAttachment()
    }
  }

  private fun PaymentTable.PaymentTransaction.getTransactionDetails(): PaymentNotification.TransactionDetails? {
    if (failureReason != null || state == State.FAILED) {
      return PaymentNotification.TransactionDetails(failedTransaction = PaymentNotification.TransactionDetails.FailedTransaction(reason = failureReason.toBackupFailureReason()))
    }
    return PaymentNotification.TransactionDetails(
      transaction = PaymentNotification.TransactionDetails.Transaction(
        status = this.state.toBackupState(),
        timestamp = timestamp,
        blockIndex = blockIndex,
        blockTimestamp = blockTimestamp,
        mobileCoinIdentification = paymentMetaData.mobileCoinTxoIdentification?.toBackup()
      )
    )
  }

  private fun PaymentMetaData.MobileCoinTxoIdentification.toBackup(): PaymentNotification.TransactionDetails.MobileCoinTxoIdentification {
    return PaymentNotification.TransactionDetails.MobileCoinTxoIdentification(
      publicKey = this.publicKey,
      keyImages = this.keyImages
    )
  }

  private fun State.toBackupState(): PaymentNotification.TransactionDetails.Transaction.Status {
    return when (this) {
      State.INITIAL -> PaymentNotification.TransactionDetails.Transaction.Status.INITIAL
      State.SUBMITTED -> PaymentNotification.TransactionDetails.Transaction.Status.SUBMITTED
      State.SUCCESSFUL -> PaymentNotification.TransactionDetails.Transaction.Status.SUCCESSFUL
      State.FAILED -> throw IllegalArgumentException("state cannot be failed")
    }
  }

  private fun FailureReason?.toBackupFailureReason(): PaymentNotification.TransactionDetails.FailedTransaction.FailureReason {
    return when (this) {
      FailureReason.UNKNOWN -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.GENERIC
      FailureReason.INSUFFICIENT_FUNDS -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.INSUFFICIENT_FUNDS
      FailureReason.NETWORK -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.NETWORK
      else -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.GENERIC
    }
  }

  private fun List<Mention>.toBackupBodyRanges(): List<BackupBodyRange> {
    return this.map {
      BackupBodyRange(
        start = it.start,
        length = it.length,
        mentionAci = SignalDatabase.recipients.getRecord(it.recipientId).aci?.toByteString()
      )
    }
  }

  private fun ByteArray.toBackupBodyRanges(): List<BackupBodyRange> {
    val decoded: BodyRangeList = try {
      BodyRangeList.ADAPTER.decode(this)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode BodyRangeList!")
      return emptyList()
    }

    return decoded.ranges.map {
      BackupBodyRange(
        start = it.start,
        length = it.length,
        mentionAci = it.mentionUuid?.let { uuid -> UuidUtil.parseOrThrow(uuid) }?.toByteArray()?.toByteString(),
        style = it.style?.toBackupBodyRangeStyle()
      )
    }
  }

  private fun BodyRangeList.BodyRange.Style.toBackupBodyRangeStyle(): BackupBodyRange.Style {
    return when (this) {
      BodyRangeList.BodyRange.Style.BOLD -> BackupBodyRange.Style.BOLD
      BodyRangeList.BodyRange.Style.ITALIC -> BackupBodyRange.Style.ITALIC
      BodyRangeList.BodyRange.Style.STRIKETHROUGH -> BackupBodyRange.Style.STRIKETHROUGH
      BodyRangeList.BodyRange.Style.MONOSPACE -> BackupBodyRange.Style.MONOSPACE
      BodyRangeList.BodyRange.Style.SPOILER -> BackupBodyRange.Style.SPOILER
    }
  }

  private fun List<ReactionRecord>?.toBackupReactions(): List<Reaction> {
    return this
      ?.map {
        Reaction(
          emoji = it.emoji,
          authorId = it.author.toLong(),
          sentTimestamp = it.dateSent,
          receivedTimestamp = it.dateReceived
        )
      } ?: emptyList()
  }

  private fun BackupMessageRecord.toBackupSendStatus(groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?): List<SendStatus> {
    if (!MessageTypes.isOutgoingMessageType(this.type)) {
      return emptyList()
    }

    if (!groupReceipts.isNullOrEmpty()) {
      return groupReceipts.toBackupSendStatus(this.networkFailureRecipientIds, this.identityMismatchRecipientIds)
    }

    val status: SendStatus.Status = when {
      this.viewed -> SendStatus.Status.VIEWED
      this.hasReadReceipt -> SendStatus.Status.READ
      this.hasDeliveryReceipt -> SendStatus.Status.DELIVERED
      this.baseType == MessageTypes.BASE_SENT_TYPE -> SendStatus.Status.SENT
      MessageTypes.isFailedMessageType(this.type) -> SendStatus.Status.FAILED
      else -> SendStatus.Status.PENDING
    }

    return listOf(
      SendStatus(
        recipientId = this.toRecipientId,
        deliveryStatus = status,
        lastStatusUpdateTimestamp = this.receiptTimestamp,
        sealedSender = this.sealedSender,
        networkFailure = this.networkFailureRecipientIds.contains(this.toRecipientId),
        identityKeyMismatch = this.identityMismatchRecipientIds.contains(this.toRecipientId)
      )
    )
  }

  private fun List<GroupReceiptTable.GroupReceiptInfo>.toBackupSendStatus(networkFailureRecipientIds: Set<Long>, identityMismatchRecipientIds: Set<Long>): List<SendStatus> {
    return this.map {
      SendStatus(
        recipientId = it.recipientId.toLong(),
        deliveryStatus = it.status.toBackupDeliveryStatus(),
        sealedSender = it.isUnidentified,
        lastStatusUpdateTimestamp = it.timestamp,
        networkFailure = networkFailureRecipientIds.contains(it.recipientId.toLong()),
        identityKeyMismatch = identityMismatchRecipientIds.contains(it.recipientId.toLong())
      )
    }
  }

  private fun Int.toBackupDeliveryStatus(): SendStatus.Status {
    return when (this) {
      GroupReceiptTable.STATUS_UNDELIVERED -> SendStatus.Status.PENDING
      GroupReceiptTable.STATUS_DELIVERED -> SendStatus.Status.DELIVERED
      GroupReceiptTable.STATUS_READ -> SendStatus.Status.READ
      GroupReceiptTable.STATUS_VIEWED -> SendStatus.Status.VIEWED
      GroupReceiptTable.STATUS_SKIPPED -> SendStatus.Status.SKIPPED
      else -> SendStatus.Status.SKIPPED
    }
  }

  private fun String?.parseNetworkFailures(): Set<Long> {
    if (this.isNullOrBlank()) {
      return emptySet()
    }

    return try {
      JsonUtils.fromJson(this, NetworkFailureSet::class.java).items.map { it.recipientId.toLong() }.toSet()
    } catch (e: IOException) {
      emptySet()
    }
  }

  private fun String?.parseIdentityMismatches(): Set<Long> {
    if (this.isNullOrBlank()) {
      return emptySet()
    }

    return try {
      JsonUtils.fromJson(this, IdentityKeyMismatchSet::class.java).items.map { it.recipientId.toLong() }.toSet()
    } catch (e: IOException) {
      emptySet()
    }
  }

  private fun ByteArray?.parseMessageExtras(): MessageExtras? {
    if (this == null) {
      return null
    }
    return try {
      MessageExtras.ADAPTER.decode(this)
    } catch (e: java.lang.Exception) {
      null
    }
  }

  private fun Long.isSmsType(): Boolean {
    if (MessageTypes.isSecureType(this)) {
      return false
    }

    if (MessageTypes.isCallLog(this)) {
      return false
    }

    return MessageTypes.isOutgoingMessageType(this) || MessageTypes.isInboxType(this)
  }

  private fun Long.isDirectionlessType(): Boolean {
    return MessageTypes.isCallLog(this) ||
      MessageTypes.isExpirationTimerUpdate(this) ||
      MessageTypes.isThreadMergeType(this) ||
      MessageTypes.isSessionSwitchoverType(this) ||
      MessageTypes.isProfileChange(this) ||
      MessageTypes.isJoinedType(this) ||
      MessageTypes.isIdentityUpdate(this) ||
      MessageTypes.isIdentityVerified(this) ||
      MessageTypes.isIdentityDefault(this) ||
      MessageTypes.isReleaseChannelDonationRequest(this) ||
      MessageTypes.isChangeNumber(this) ||
      MessageTypes.isEndSessionType(this) ||
      MessageTypes.isChatSessionRefresh(this) ||
      MessageTypes.isBadDecryptType(this) ||
      MessageTypes.isPaymentsActivated(this) ||
      MessageTypes.isPaymentsRequestToActivate(this) ||
      MessageTypes.isUnsupportedMessageType(this) ||
      MessageTypes.isReportedSpam(this)
  }

  private fun String.e164ToLong(): Long? {
    val fixed = if (this.startsWith("+")) {
      this.substring(1)
    } else {
      this
    }

    return fixed.toLongOrNull()
  }

  private fun Cursor.toBackupMessageRecord(): BackupMessageRecord {
    return BackupMessageRecord(
      id = this.requireLong(MessageTable.ID),
      dateSent = this.requireLong(MessageTable.DATE_SENT),
      dateReceived = this.requireLong(MessageTable.DATE_RECEIVED),
      dateServer = this.requireLong(MessageTable.DATE_SERVER),
      type = this.requireLong(MessageTable.TYPE),
      threadId = this.requireLong(MessageTable.THREAD_ID),
      body = this.requireString(MessageTable.BODY),
      bodyRanges = this.requireBlob(MessageTable.MESSAGE_RANGES),
      fromRecipientId = this.requireLong(MessageTable.FROM_RECIPIENT_ID),
      toRecipientId = this.requireLong(MessageTable.TO_RECIPIENT_ID),
      expiresIn = this.requireLong(MessageTable.EXPIRES_IN),
      expireStarted = this.requireLong(MessageTable.EXPIRE_STARTED),
      remoteDeleted = this.requireBoolean(MessageTable.REMOTE_DELETED),
      sealedSender = this.requireBoolean(MessageTable.UNIDENTIFIED),
      linkPreview = this.requireString(MessageTable.LINK_PREVIEWS),
      sharedContacts = this.requireString(MessageTable.SHARED_CONTACTS),
      quoteTargetSentTimestamp = this.requireLong(MessageTable.QUOTE_ID),
      quoteAuthor = this.requireLong(MessageTable.QUOTE_AUTHOR),
      quoteBody = this.requireString(MessageTable.QUOTE_BODY),
      quoteMissing = this.requireBoolean(MessageTable.QUOTE_MISSING),
      quoteBodyRanges = this.requireBlob(MessageTable.QUOTE_BODY_RANGES),
      quoteType = this.requireInt(MessageTable.QUOTE_TYPE),
      originalMessageId = this.requireLongOrNull(MessageTable.ORIGINAL_MESSAGE_ID),
      latestRevisionId = this.requireLongOrNull(MessageTable.LATEST_REVISION_ID),
      hasDeliveryReceipt = this.requireBoolean(MessageTable.HAS_DELIVERY_RECEIPT),
      viewed = this.requireBoolean(MessageTable.VIEWED_COLUMN),
      hasReadReceipt = this.requireBoolean(MessageTable.HAS_READ_RECEIPT),
      read = this.requireBoolean(MessageTable.READ),
      receiptTimestamp = this.requireLong(MessageTable.RECEIPT_TIMESTAMP),
      networkFailureRecipientIds = this.requireString(MessageTable.NETWORK_FAILURES).parseNetworkFailures(),
      identityMismatchRecipientIds = this.requireString(MessageTable.MISMATCHED_IDENTITIES).parseIdentityMismatches(),
      baseType = this.requireLong(COLUMN_BASE_TYPE),
      messageExtras = this.requireBlob(MessageTable.MESSAGE_EXTRAS).parseMessageExtras()
    )
  }

  private class BackupMessageRecord(
    val id: Long,
    val dateSent: Long,
    val dateReceived: Long,
    val dateServer: Long,
    val type: Long,
    val threadId: Long,
    val body: String?,
    val bodyRanges: ByteArray?,
    val fromRecipientId: Long,
    val toRecipientId: Long,
    val expiresIn: Long,
    val expireStarted: Long,
    val remoteDeleted: Boolean,
    val sealedSender: Boolean,
    val linkPreview: String?,
    val sharedContacts: String?,
    val quoteTargetSentTimestamp: Long,
    val quoteAuthor: Long,
    val quoteBody: String?,
    val quoteMissing: Boolean,
    val quoteBodyRanges: ByteArray?,
    val quoteType: Int,
    val originalMessageId: Long?,
    val latestRevisionId: Long?,
    val hasDeliveryReceipt: Boolean,
    val hasReadReceipt: Boolean,
    val viewed: Boolean,
    val receiptTimestamp: Long,
    val read: Boolean,
    val networkFailureRecipientIds: Set<Long>,
    val identityMismatchRecipientIds: Set<Long>,
    val baseType: Long,
    val messageExtras: MessageExtras?
  )
}
