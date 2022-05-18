package org.thoughtcrime.securesms.conversation.mutiselect.forward

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.MultiShareSender
import java.util.Optional

class MultiselectForwardRepository {

  class MultiselectForwardResultHandlers(
    val onAllMessageSentSuccessfully: () -> Unit,
    val onSomeMessagesFailed: () -> Unit,
    val onAllMessagesFailed: () -> Unit
  )

  fun canSelectRecipient(recipientId: Optional<RecipientId>): Single<Boolean> {
    if (!recipientId.isPresent) {
      return Single.just(true)
    }

    return Single.fromCallable {
      val recipient = Recipient.resolved(recipientId.get())
      if (recipient.isPushV2Group) {
        val record = SignalDatabase.groups.getGroup(recipient.requireGroupId())
        !(record.isPresent && record.get().isAnnouncementGroup && !record.get().isAdmin(Recipient.self()))
      } else {
        true
      }
    }
  }

  fun send(
    additionalMessage: String,
    multiShareArgs: List<MultiShareArgs>,
    shareContacts: Set<ContactSearchKey>,
    resultHandlers: MultiselectForwardResultHandlers
  ) {
    SignalExecutors.BOUNDED.execute {
      val filteredContacts: Set<ContactSearchKey> = shareContacts
        .asSequence()
        .filter { it is ContactSearchKey.RecipientSearchKey.Story || it is ContactSearchKey.RecipientSearchKey.KnownRecipient }
        .toSet()

      val mappedArgs: List<MultiShareArgs> = multiShareArgs.map { it.buildUpon(filteredContacts).build() }
      val results = mappedArgs.sortedBy { it.timestamp }.map { MultiShareSender.sendSync(it) }

      if (additionalMessage.isNotEmpty()) {
        val additional = MultiShareArgs.Builder(filteredContacts.filterNot { it is ContactSearchKey.RecipientSearchKey.Story }.toSet())
          .withDraftText(additionalMessage)
          .build()

        if (additional.contactSearchKeys.isNotEmpty()) {
          val additionalResult: MultiShareSender.MultiShareSendResultCollection = MultiShareSender.sendSync(additional)

          handleResults(results + additionalResult, resultHandlers)
        } else {
          handleResults(results, resultHandlers)
        }
      } else {
        handleResults(results, resultHandlers)
      }
    }
  }

  private fun handleResults(
    results: List<MultiShareSender.MultiShareSendResultCollection>,
    resultHandlers: MultiselectForwardResultHandlers
  ) {
    if (results.any { it.containsFailures() }) {
      if (results.all { it.containsOnlyFailures() }) {
        resultHandlers.onAllMessagesFailed()
      } else {
        resultHandlers.onSomeMessagesFailed()
      }
    } else {
      resultHandlers.onAllMessageSentSuccessfully()
    }
  }
}
