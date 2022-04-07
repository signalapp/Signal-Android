package org.thoughtcrime.securesms.mediasend.v2

import androidx.core.util.Consumer
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient

object UntrustedRecords {

  fun checkForBadIdentityRecords(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>): Completable {
    return Completable.fromAction {
      val untrustedRecords: List<IdentityRecord> = checkForBadIdentityRecordsSync(contactSearchKeys)
      if (untrustedRecords.isNotEmpty()) {
        throw UntrustedRecordsException(untrustedRecords)
      }
    }.subscribeOn(Schedulers.io())
  }

  fun checkForBadIdentityRecords(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>, consumer: Consumer<List<IdentityRecord>>) {
    SignalExecutors.BOUNDED.execute {
      consumer.accept(checkForBadIdentityRecordsSync(contactSearchKeys))
    }
  }

  private fun checkForBadIdentityRecordsSync(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>): List<IdentityRecord> {
    val recipients: List<Recipient> = contactSearchKeys
      .map { Recipient.resolved(it.recipientId) }
      .map { recipient ->
        when {
          recipient.isGroup -> recipient.participants
          recipient.isDistributionList -> Recipient.resolvedList(SignalDatabase.distributionLists.getMembers(recipient.distributionListId.get()))
          else -> listOf(recipient)
        }
      }
      .flatten()

    return ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients).untrustedRecords
  }

  class UntrustedRecordsException(val untrustedRecords: List<IdentityRecord>) : Throwable()
}
