package org.thoughtcrime.securesms.mms

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext

class IncomingMediaMessage(
  val from: RecipientId?,
  val groupId: GroupId? = null,
  val body: String? = null,
  val isPushMessage: Boolean = false,
  val storyType: StoryType = StoryType.NONE,
  val parentStoryId: ParentStoryId? = null,
  val sentTimeMillis: Long,
  val serverTimeMillis: Long,
  val receivedTimeMillis: Long,
  val subscriptionId: Int = -1,
  val expiresIn: Long = 0,
  val isExpirationUpdate: Boolean = false,
  val quote: QuoteModel? = null,
  val isUnidentified: Boolean = false,
  val isViewOnce: Boolean = false,
  val serverGuid: String? = null,
  val messageRanges: BodyRangeList? = null,
  attachments: List<Attachment> = emptyList(),
  sharedContacts: List<Contact> = emptyList(),
  linkPreviews: List<LinkPreview> = emptyList(),
  mentions: List<Mention> = emptyList()
) {

  val attachments: List<Attachment> = ArrayList(attachments)
  val sharedContacts: List<Contact> = ArrayList(sharedContacts)
  val linkPreviews: List<LinkPreview> = ArrayList(linkPreviews)
  val mentions: List<Mention> = ArrayList(mentions)

  val isGroupMessage: Boolean = groupId != null

  constructor(
    from: RecipientId?,
    groupId: Optional<GroupId>,
    body: String?,
    sentTimeMillis: Long,
    serverTimeMillis: Long,
    receivedTimeMillis: Long,
    attachments: List<Attachment>?,
    subscriptionId: Int,
    expiresIn: Long,
    expirationUpdate: Boolean,
    viewOnce: Boolean,
    unidentified: Boolean,
    sharedContacts: Optional<List<Contact>>
  ) : this(
    from = from,
    groupId = groupId.orNull(),
    body = body,
    isPushMessage = false,
    sentTimeMillis = sentTimeMillis,
    serverTimeMillis = serverTimeMillis,
    receivedTimeMillis = receivedTimeMillis,
    subscriptionId = subscriptionId,
    expiresIn = expiresIn,
    isExpirationUpdate = expirationUpdate,
    quote = null,
    isUnidentified = unidentified,
    isViewOnce = viewOnce,
    serverGuid = null,
    attachments = ArrayList(attachments),
    sharedContacts = ArrayList(sharedContacts.or(emptyList()))
  )

  constructor(
    from: RecipientId?,
    sentTimeMillis: Long,
    serverTimeMillis: Long,
    receivedTimeMillis: Long,
    storyType: StoryType,
    parentStoryId: ParentStoryId?,
    subscriptionId: Int,
    expiresIn: Long,
    expirationUpdate: Boolean,
    viewOnce: Boolean,
    unidentified: Boolean,
    body: Optional<String>,
    group: Optional<SignalServiceGroupContext>,
    attachments: Optional<List<SignalServiceAttachment>>,
    quote: Optional<QuoteModel>,
    sharedContacts: Optional<List<Contact>>,
    linkPreviews: Optional<List<LinkPreview>>,
    mentions: Optional<List<Mention>>,
    sticker: Optional<Attachment>,
    serverGuid: String?
  ) : this(
    from = from,
    groupId = if (group.isPresent) GroupUtil.idFromGroupContextOrThrow(group.get()) else null,
    body = body.orNull(),
    isPushMessage = true,
    storyType = storyType,
    parentStoryId = parentStoryId,
    sentTimeMillis = sentTimeMillis,
    serverTimeMillis = serverTimeMillis,
    receivedTimeMillis = receivedTimeMillis,
    subscriptionId = subscriptionId,
    expiresIn = expiresIn,
    isExpirationUpdate = expirationUpdate,
    quote = quote.orNull(),
    isUnidentified = unidentified,
    isViewOnce = viewOnce,
    serverGuid = serverGuid,
    attachments = PointerAttachment.forPointers(attachments).apply { if (sticker.isPresent) add(sticker.get()) },
    sharedContacts = sharedContacts.or(emptyList()),
    linkPreviews = linkPreviews.or(emptyList()),
    mentions = mentions.or(emptyList())
  )
}
