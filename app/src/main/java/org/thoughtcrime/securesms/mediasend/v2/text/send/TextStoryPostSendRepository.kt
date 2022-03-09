package org.thoughtcrime.securesms.mediasend.v2.text.send

import android.content.Context
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.ThreadUtil
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.RecipientSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.identity.IdentityRecordList
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.Base64

class TextStoryPostSendRepository(context: Context) {

  private val context = context.applicationContext

  fun isFirstSendToStory(shareContacts: Set<ContactSearchKey>): Boolean {
    if (SignalStore.storyValues().userHasAddedToAStory) {
      return false
    }

    return shareContacts.any { it is ContactSearchKey.Story }
  }

  fun send(contactSearchKey: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreview: LinkPreview?): Single<TextStoryPostSendResult> {
    return checkForBadIdentityRecords(contactSearchKey).flatMap { result ->
      if (result is TextStoryPostSendResult.Success) {
        performSend(contactSearchKey, textStoryPostCreationState, linkPreview)
      } else {
        Single.just(result)
      }
    }
  }

  private fun checkForBadIdentityRecords(contactSearchKeys: Set<ContactSearchKey>): Single<TextStoryPostSendResult> {
    return Single.fromCallable {
      val recipients: List<Recipient> = contactSearchKeys
        .filterIsInstance<RecipientSearchKey>()
        .map { Recipient.resolved(it.recipientId) }
      val identityRecordList: IdentityRecordList = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients)

      if (identityRecordList.untrustedRecords.isNotEmpty()) {
        TextStoryPostSendResult.UntrustedRecordsError(identityRecordList.untrustedRecords)
      } else {
        TextStoryPostSendResult.Success
      }
    }.subscribeOn(Schedulers.io())
  }

  private fun performSend(contactSearchKey: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreview: LinkPreview?): Single<TextStoryPostSendResult> {
    return Single.fromCallable {
      val messages: MutableList<OutgoingSecureMediaMessage> = mutableListOf()

      for (contact in contactSearchKey) {
        val recipient = Recipient.resolved(contact.requireShareContact().recipientId.get())
        val isStory = contact is ContactSearchKey.Story || recipient.isDistributionList

        if (isStory && recipient.isActiveGroup) {
          SignalDatabase.groups.markDisplayAsStory(recipient.requireGroupId())
        }

        val storyType: StoryType = when {
          recipient.isDistributionList -> SignalDatabase.distributionLists.getStoryType(recipient.requireDistributionListId())
          isStory -> StoryType.STORY_WITH_REPLIES
          else -> StoryType.NONE
        }

        val message = OutgoingMediaMessage(
          recipient,
          serializeTextStoryState(textStoryPostCreationState),
          emptyList(),
          System.currentTimeMillis(),
          -1,
          0,
          false,
          ThreadDatabase.DistributionTypes.DEFAULT,
          storyType.toTextStoryType(),
          null,
          null,
          emptyList(),
          listOfNotNull(linkPreview),
          emptyList(),
          mutableSetOf(),
          mutableSetOf()
        )

        messages.add(OutgoingSecureMediaMessage(message))
        ThreadUtil.sleep(5)
      }

      MessageSender.sendMediaBroadcast(context, messages, emptyList())
      TextStoryPostSendResult.Success
    }
  }

  private fun serializeTextStoryState(textStoryPostCreationState: TextStoryPostCreationState): String {
    val builder = StoryTextPost.newBuilder()

    builder.body = textStoryPostCreationState.body.toString()
    builder.background = textStoryPostCreationState.backgroundColor.serialize()
    builder.style = when (textStoryPostCreationState.textFont) {
      TextFont.REGULAR -> StoryTextPost.Style.REGULAR
      TextFont.BOLD -> StoryTextPost.Style.BOLD
      TextFont.SERIF -> StoryTextPost.Style.SERIF
      TextFont.SCRIPT -> StoryTextPost.Style.SCRIPT
      TextFont.CONDENSED -> StoryTextPost.Style.CONDENSED
    }
    builder.textBackgroundColor = textStoryPostCreationState.textBackgroundColor
    builder.textForegroundColor = textStoryPostCreationState.textForegroundColor

    return Base64.encodeBytes(builder.build().toByteArray())
  }
}
