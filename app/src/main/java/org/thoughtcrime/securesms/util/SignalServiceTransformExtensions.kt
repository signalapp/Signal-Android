/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.util

import arrow.core.Either
import arrow.core.raise.context.bind
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.UuidUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.PinnedMessage
import org.thoughtcrime.securesms.database.model.databaseprotos.PollTerminate
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.polls.Poll
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.CallMessage
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Preview
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.IOException

private const val TAG = "DataMessageTransforms"

/**
 * Builds the wire [DataMessage] for this outgoing message. It is technically possible, though rare, that we may not be
 * able to successfully construct a model. These are almost certainly data consistency bugs, and we'd rather fail the
 * send than send something that doesn't match the user intent.
 */
fun OutgoingMessage.toDataMessage(): Either<DataMessageError, DataMessage> = either {
  val builder = DataMessage.Builder()

  builder.body = body.ifEmpty { null }
  builder.timestamp = sentTimeMillis
  builder.profileKey = threadRecipient.fresh().selfProfileKeyForOutgoing()
  builder.sticker = attachments.toStickerIfPresent().bind()
  builder.contact = sharedContacts.map { it.toProto().bind() }
  builder.preview = linkPreviews.map { it.toProto().bind() }
  builder.giftBadge = giftBadge?.toProto()?.bind()
  builder.bodyRanges = bodyRanges?.toProto()?.bind() ?: emptyList()
  builder.pollCreate = poll?.toProto()
  builder.pollTerminate = messageExtras?.pollTerminate?.toProto()
  builder.pinMessage = messageExtras?.pinnedMessage?.toProto()?.bind()
  builder.payment = toPaymentProtoIfPresent().bind()
  builder.isViewOnce = isViewOnce
  builder.flags = if (isExpirationUpdate) DataMessage.Flags.EXPIRATION_TIMER_UPDATE.value else null
  builder.expireTimer = (expiresIn / 1000).toInt()
  builder.expireTimerVersion = expireTimerVersion
  builder.attachments = attachments
    .filter { !it.isSticker }
    .map { it.toAttachmentPointerProto().bind() }
    .capIncrementalMacs(RemoteConfig.maxIncrementalMacsPerEnvelope)

  if (giftBadge != null || isPaymentsNotification) {
    builder.body = null
  }

  if (parentStoryId != null) {
    val storyRecord = ensureNotNull(SignalDatabase.messages.getMessageRecordOrNull(parentStoryId.asMessageId().id)) {
      DataMessageError.MissingParentStory
    }
    val storyAuthor = storyRecord.fromRecipient.requireServiceId()
    builder.storyContext = DataMessage.StoryContext(
      authorAciBinary = storyAuthor.toByteString(),
      sentTimestamp = storyRecord.dateSent
    )

    if (isStoryReaction) {
      builder.reaction = DataMessage.Reaction(
        emoji = body,
        remove = false,
        targetAuthorAciBinary = storyAuthor.toByteString(),
        targetSentTimestamp = storyRecord.dateSent
      )
      builder.body = null
    }
  } else {
    builder.quote = outgoingQuote?.toProto(isMessageEdit)?.bind()
  }

  builder.requiredProtocolVersion = builder.getRequiredProtocolVersion(isViewOnce)

  builder.build()
}

private fun DataMessage.Builder.getRequiredProtocolVersion(isViewOnce: Boolean): Int? {
  var version = 0

  if (isViewOnce) {
    version = maxOf(version, DataMessage.ProtocolVersion.VIEW_ONCE_VIDEO.value)
  }

  if (reaction != null) {
    version = maxOf(version, DataMessage.ProtocolVersion.REACTIONS.value)
  }

  if (payment != null) {
    version = maxOf(version, DataMessage.ProtocolVersion.PAYMENTS.value)
  }

  if (pollCreate != null) {
    version = maxOf(version, DataMessage.ProtocolVersion.POLLS.value)
  }

  return version.takeIf { it > 0 }
}

private fun QuoteModel.toProto(isMessageEdit: Boolean): Either<DataMessageError, DataMessage.Quote> = either {
  if (isMessageEdit) {
    return@either DataMessage.Quote(
      id = 0,
      authorAciBinary = ACI.UNKNOWN.toByteString(),
      text = "",
      type = DataMessage.Quote.Type.NORMAL
    )
  }

  val quoteAuthor = Recipient.resolved(author)
  ensure(quoteAuthor.hasServiceId) { DataMessageError.MissingQuoteAuthorServiceId }

  val mentionBodyRanges: List<BodyRange> = mentions.map { mention ->
    BodyRange(
      start = mention.start,
      length = mention.length,
      mentionAciBinary = Recipient.resolved(mention.recipientId).requireAci().toByteString()
    )
  }

  val combinedBodyRanges: List<BodyRange> = mentionBodyRanges + (bodyRanges?.toProto()?.bind() ?: emptyList())

  val quoteAttachments = attachment
    ?.takeUnless { MediaUtil.isViewOnceType(attachment.contentType) }
    ?.toQuoteAttachmentProto()
    ?.bind()
    ?.let { listOf(it) }

  DataMessage.Quote(
    id = id,
    authorAciBinary = quoteAuthor.requireAci().toByteString(),
    text = text,
    attachments = quoteAttachments ?: emptyList(),
    bodyRanges = combinedBodyRanges,
    type = type.dataMessageType.protoType
  )
}

private fun Attachment.toQuoteAttachmentProto(): Either<DataMessageError, DataMessage.Quote.QuotedAttachment> = either {
  DataMessage.Quote.QuotedAttachment(
    contentType = quoteTargetContentType ?: MediaUtil.IMAGE_JPEG,
    fileName = fileName,
    thumbnail = toAttachmentPointerProto().bind()
  )
}

private fun OutgoingMessage.toPaymentProtoIfPresent(): Either<DataMessageError, DataMessage.Payment?> = either {
  when {
    isPaymentsNotification -> {
      val paymentUuid = UuidUtil.parseOrThrow(body)
      val payment = ensureNotNull(SignalDatabase.payments.getPayment(paymentUuid)) { DataMessageError.MissingPayment }
      val receipt = ensureNotNull(payment.receipt) { DataMessageError.MissingPaymentReceipt }

      DataMessage.Payment(
        notification = DataMessage.Payment.Notification(
          note = payment.note,
          mobileCoin = DataMessage.Payment.Notification.MobileCoin(receipt = receipt.toByteString())
        )
      )
    }
    isRequestToActivatePayments -> {
      DataMessage.Payment(activation = DataMessage.Payment.Activation(type = DataMessage.Payment.Activation.Type.REQUEST))
    }
    isPaymentsActivated -> {
      DataMessage.Payment(activation = DataMessage.Payment.Activation(type = DataMessage.Payment.Activation.Type.ACTIVATED))
    }
    else -> {
      null
    }
  }
}

private fun Recipient.selfProfileKeyForOutgoing(): ByteString? {
  val resolved = this.resolve()
  return if (resolved.isSystemContact || resolved.isProfileSharing) {
    ProfileKeyUtil.getSelfProfileKey().serialize().toByteString()
  } else {
    null
  }
}

private fun Attachment.toAttachmentPointerProto(): Either<DataMessageError, AttachmentPointer> = either {
  if (remoteLocation.isNullOrEmpty() || remoteKey.isNullOrEmpty() || remoteDigest == null) {
    raise(DataMessageError.MissingAttachmentRemoteFields)
  }

  val remoteIdResolved: SignalServiceAttachmentRemoteId = SignalServiceAttachmentRemoteId.from(remoteLocation)

  val keyBytes: ByteArray = try {
    Base64.decode(remoteKey)
  } catch (_: IOException) {
    raise(DataMessageError.FailedToDecodeAttachmentKey)
  }

  val sizeInt: Int = try {
    Math.toIntExact(size)
  } catch (_: ArithmeticException) {
    Log.w(TAG, "Failed to parse attachment size! Skipping attachment.")
    raise(DataMessageError.FailedToDecodeAttachmentSize)
  }

  var flags = 0
  if (voiceNote) {
    flags = flags or AttachmentPointer.Flags.VOICE_MESSAGE.value
  }
  if (borderless) {
    flags = flags or AttachmentPointer.Flags.BORDERLESS.value
  }
  if (videoGif) {
    flags = flags or AttachmentPointer.Flags.GIF.value
  }

  val builder = AttachmentPointer.Builder()
    .cdnNumber(cdn.cdnNumber)
    .contentType(contentType)
    .key(keyBytes.toByteString())
    .digest(remoteDigest.toByteString())
    .size(sizeInt)
    .uploadTimestamp(uploadTimestamp)
    .flags(flags)

  when (remoteIdResolved) {
    is SignalServiceAttachmentRemoteId.V2 -> builder.cdnId(remoteIdResolved.cdnId)
    is SignalServiceAttachmentRemoteId.V4 -> builder.cdnKey(remoteIdResolved.cdnKey)
    is SignalServiceAttachmentRemoteId.S3,
    is SignalServiceAttachmentRemoteId.Backup -> Unit
  }

  incrementalDigest?.let { builder.incrementalMac(it.toByteString()) }
  incrementalMacChunkSize.takeIf { it > 0 }?.let { builder.chunkSize(incrementalMacChunkSize) }
  width.takeIf { it > 0 }?.let { builder.width(it) }
  height.takeIf { it > 0 }?.let { builder.height(it) }
  fileName?.let { builder.fileName(it) }
  caption?.let { builder.caption(it) }
  blurHash?.let { builder.blurHash(it.hash) }
  uuid?.let { builder.clientUuid(UuidUtil.toByteString(it)) }

  builder.build()
}

private fun List<Attachment>.toStickerIfPresent(): Either<DataMessageError, DataMessage.Sticker?> = either {
  val stickerAttachment = firstOrNull { it.isSticker } ?: return@either null
  val locator = ensureNotNull(stickerAttachment.stickerLocator) { DataMessageError.MissingStickerLocator }

  try {
    val packId = Hex.fromStringCondensed(locator.packId)
    val packKey = Hex.fromStringCondensed(locator.packKey)
    val emoji = SignalDatabase.stickers.getSticker(locator.packId, locator.stickerId, false)?.emoji
    DataMessage.Sticker(
      packId = packId.toByteString(),
      packKey = packKey.toByteString(),
      stickerId = locator.stickerId,
      emoji = emoji,
      data_ = stickerAttachment.toAttachmentPointerProto().bind()
    )
  } catch (e: IOException) {
    Log.w(TAG, "Failed to decode sticker pack fields.", e)
    raise(DataMessageError.FailedToDecodeStickerPackFields)
  }
}

private fun GiftBadge.toProto(): Either<DataMessageError, DataMessage.GiftBadge> = either {
  try {
    val presentation = ReceiptCredentialPresentation(redemptionToken.toByteArray())
    DataMessage.GiftBadge(receiptCredentialPresentation = presentation.serialize().toByteString())
  } catch (e: InvalidInputException) {
    Log.w(TAG, "Failed to parse gift badge.", e)
    raise(DataMessageError.InvalidGiftBadge)
  }
}

private fun BodyRangeList.toProto(): Either<DataMessageError, List<BodyRange>> = either {
  if (ranges.isEmpty()) {
    return@either emptyList()
  }

  ranges.map { range ->
    val style = when (range.style) {
      BodyRangeList.BodyRange.Style.BOLD -> BodyRange.Style.BOLD
      BodyRangeList.BodyRange.Style.ITALIC -> BodyRange.Style.ITALIC
      BodyRangeList.BodyRange.Style.SPOILER -> BodyRange.Style.SPOILER
      BodyRangeList.BodyRange.Style.STRIKETHROUGH -> BodyRange.Style.STRIKETHROUGH
      BodyRangeList.BodyRange.Style.MONOSPACE -> BodyRange.Style.MONOSPACE
      null -> raise(DataMessageError.InvalidBodyRange)
    }
    BodyRange.Builder().start(range.start).length(range.length).style(style).build()
  }
}

private fun Poll.toProto(): DataMessage.PollCreate {
  return DataMessage.PollCreate(
    question = this.question,
    allowMultiple = this.allowMultipleVotes,
    options = this.pollOptions
  )
}

private fun PollTerminate.toProto(): DataMessage.PollTerminate {
  return DataMessage.PollTerminate(targetSentTimestamp = this.targetTimestamp)
}

private fun PinnedMessage.toProto(): Either<DataMessageError, DataMessage.PinMessage> = either {
  val targetAuthor = ensureNotNull(ServiceId.parseOrNull(targetAuthorAci)) { DataMessageError.PinnedMessageInvalidAuthorAci }
  val forever = pinDurationInSeconds == MessageTable.PIN_FOREVER
  DataMessage.PinMessage(
    targetAuthorAciBinary = targetAuthor.toByteString(),
    targetSentTimestamp = targetTimestamp,
    pinDurationSeconds = if (!forever) pinDurationInSeconds.toInt() else null,
    pinDurationForever = if (forever) true else null
  )
}

private fun LinkPreview.toProto(): Either<DataMessageError, Preview> = either {
  Preview(
    url = url,
    title = title,
    description = description,
    date = date,
    image = thumbnail.orElse(null)?.toAttachmentPointerProto()?.bind()
  )
}

private fun Contact.toProto(): Either<DataMessageError, DataMessage.Contact> = either {
  DataMessage.Contact(
    name = DataMessage.Contact.Name(
      givenName = name.givenName,
      familyName = name.familyName,
      prefix = name.prefix,
      suffix = name.suffix,
      middleName = name.middleName,
      nickname = name.nickname
    ),
    number = phoneNumbers.map {
      DataMessage.Contact.Phone(value_ = it.number, type = it.type.toProto(), label = it.label)
    },
    email = emails.map {
      DataMessage.Contact.Email(value_ = it.email, type = it.type.toProto(), label = it.label)
    },
    address = postalAddresses.map {
      DataMessage.Contact.PostalAddress(
        type = it.type.toProto(),
        label = it.label,
        street = it.street,
        pobox = it.poBox,
        neighborhood = it.neighborhood,
        city = it.city,
        region = it.region,
        postcode = it.postalCode,
        country = it.country
      )
    },
    avatar = avatar?.let { avatar ->
      avatar.attachment
        ?.toAttachmentPointerProto()
        ?.map { DataMessage.Contact.Avatar(avatar = it, isProfile = avatar.isProfile) }
        ?.bind()
    },
    organization = organization
  )
}

private fun Contact.Phone.Type.toProto(): DataMessage.Contact.Phone.Type {
  return when (this) {
    Contact.Phone.Type.HOME -> DataMessage.Contact.Phone.Type.HOME
    Contact.Phone.Type.MOBILE -> DataMessage.Contact.Phone.Type.MOBILE
    Contact.Phone.Type.WORK -> DataMessage.Contact.Phone.Type.WORK
    Contact.Phone.Type.CUSTOM -> DataMessage.Contact.Phone.Type.CUSTOM
  }
}

private fun Contact.Email.Type.toProto(): DataMessage.Contact.Email.Type {
  return when (this) {
    Contact.Email.Type.HOME -> DataMessage.Contact.Email.Type.HOME
    Contact.Email.Type.MOBILE -> DataMessage.Contact.Email.Type.MOBILE
    Contact.Email.Type.WORK -> DataMessage.Contact.Email.Type.WORK
    Contact.Email.Type.CUSTOM -> DataMessage.Contact.Email.Type.CUSTOM
  }
}

private fun Contact.PostalAddress.Type.toProto(): DataMessage.Contact.PostalAddress.Type {
  return when (this) {
    Contact.PostalAddress.Type.HOME -> DataMessage.Contact.PostalAddress.Type.HOME
    Contact.PostalAddress.Type.WORK -> DataMessage.Contact.PostalAddress.Type.WORK
    Contact.PostalAddress.Type.CUSTOM -> DataMessage.Contact.PostalAddress.Type.CUSTOM
  }
}

/**
 * Strips `incrementalMac` (and its sibling `chunkSize`) from attachments past the [max]th one
 * that carries an incremental MAC, mirroring `SignalServiceMessageSender.capIncrementalMacs`.
 * [max] <= 0 disables the cap.
 */
private fun List<AttachmentPointer>.capIncrementalMacs(max: Int): List<AttachmentPointer> {
  if (max <= 0) {
    return this
  }

  val incrementalCount = count { it.incrementalMac != null }

  if (incrementalCount <= max) {
    return this
  }

  var kept = 0
  return map { pointer ->
    if (pointer.incrementalMac == null) {
      pointer
    } else if (kept < max) {
      kept++
      pointer
    } else {
      pointer.newBuilder().incrementalMac(null).chunkSize(null).build()
    }
  }
}

/**
 * Whether or not the content should generate a high-priority push notification for the receiver.
 */
fun Content.isUrgent(): Boolean {
  dataMessage?.let { return it.isUrgent() }
  editMessage?.let { return it.dataMessage?.isUrgent() ?: false }
  syncMessage?.let { return it.isUrgent() }
  callMessage?.let { return it.isUrgent() }

  return false
}

private fun DataMessage.isUrgent(): Boolean {
  val flagsValue = this.flags ?: 0

  if (flagsValue and DataMessage.Flags.EXPIRATION_TIMER_UPDATE.value != 0) {
    return false
  }

  if (flagsValue and DataMessage.Flags.PROFILE_KEY_UPDATE.value != 0) {
    return false
  }

  return !this.body.isNullOrEmpty() ||
    this.attachments.isNotEmpty() ||
    this.sticker != null ||
    this.reaction != null ||
    this.quote != null ||
    this.contact.isNotEmpty() ||
    this.giftBadge != null ||
    this.pollCreate != null ||
    this.pollTerminate != null ||
    this.pinMessage != null ||
    this.delete != null ||
    this.payment?.notification != null
}

private fun SyncMessage.isUrgent(): Boolean {
  if (this.read.isNotEmpty()) {
    return true
  }

  this.request?.let { req ->
    return when (req.type) {
      SyncMessage.Request.Type.CONTACTS, SyncMessage.Request.Type.KEYS -> true
      else -> false
    }
  }

  this.callEvent?.let { event ->
    return event.event == SyncMessage.CallEvent.Event.ACCEPTED
  }

  return false
}

private fun CallMessage.isUrgent(): Boolean {
  if (offer != null) {
    return true
  }

  if (opaque?.urgency == CallMessage.Opaque.Urgency.HANDLE_IMMEDIATELY) {
    return true
  }

  return false
}

sealed interface DataMessageError {
  data object MissingParentStory : DataMessageError
  data object MissingQuoteAuthorServiceId : DataMessageError
  data object MissingPayment : DataMessageError
  data object MissingPaymentReceipt : DataMessageError
  data object MissingAttachmentRemoteFields : DataMessageError
  data object FailedToDecodeAttachmentKey : DataMessageError
  data object FailedToDecodeAttachmentSize : DataMessageError
  data object FailedToDecodeStickerPackFields : DataMessageError
  data object MissingStickerLocator : DataMessageError
  data object PinnedMessageInvalidAuthorAci : DataMessageError
  data object InvalidGiftBadge : DataMessageError
  data object InvalidBodyRange : DataMessageError
}
