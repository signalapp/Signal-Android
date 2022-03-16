package org.thoughtcrime.securesms.stories

import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseStoryTypeBottomSheet
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.FeatureFlags

object Stories {
  @JvmStatic
  fun isFeatureAvailable(): Boolean {
    return FeatureFlags.stories() && Recipient.self().storiesCapability == Recipient.Capability.SUPPORTED
  }

  @JvmStatic
  fun isFeatureEnabled(): Boolean {
    return isFeatureAvailable() && !SignalStore.storyValues().isFeatureDisabled
  }

  fun getHeaderAction(fragmentManager: FragmentManager): HeaderAction {
    return HeaderAction(
      R.string.ContactsCursorLoader_new_story,
      R.drawable.ic_plus_20
    ) {
      ChooseStoryTypeBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @WorkerThread
  fun sendIndividualStory(message: OutgoingMediaMessage): Completable {
    return Completable.create { emitter ->
      MessageSender.send(
        ApplicationDependencies.getApplication(),
        message,
        -1L,
        false,
        null
      ) {
        emitter.onComplete()
      }
    }
  }

  @JvmStatic
  fun getRecipientsToSendTo(distributionListId: DistributionListId, messageId: Long): List<Recipient> {
    val destinations: List<GroupReceiptInfo> = SignalDatabase.groupReceipts.getGroupReceiptInfo(messageId)

    val recipientIds: List<RecipientId> = if (destinations.isNotEmpty()) {
      destinations.map(GroupReceiptInfo::getRecipientId)
    } else {
      SignalDatabase.distributionLists.getMembers(distributionListId)
    }

    return RecipientUtil.getEligibleForSending(recipientIds.map(Recipient::resolved))
  }
}
