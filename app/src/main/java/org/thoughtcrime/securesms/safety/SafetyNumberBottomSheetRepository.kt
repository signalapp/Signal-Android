package org.thoughtcrime.securesms.safety

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.Stories
import java.util.Optional

/**
 * Repository for dealing with recipients with changed safety numbers.
 */
class SafetyNumberBottomSheetRepository {

  /**
   * Retrieves the IdentityRecord for a given recipient from the protocol store (if present)
   */
  fun getIdentityRecord(recipientId: RecipientId): Maybe<IdentityRecord> {
    return Single.fromCallable {
      AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipientId)
    }.flatMapMaybe {
      if (it.isPresent) {
        Maybe.just(it.get())
      } else {
        Maybe.empty()
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Builds out the list of UntrustedRecipients to display in either the bottom sheet or review fragment. This list will be automatically
   * republished whenever there is a recipient change, group change, or distribution list change, and is driven by Recipient.observe.
   *
   * It will automatically filter out any recipients who are no longer included in destinations as the user moves through the list and removes or
   * verifies senders.
   */
  fun getBuckets(recipients: List<RecipientId>, destinations: List<ContactSearchKey.RecipientSearchKey>): Flowable<Map<SafetyNumberBucket, List<SafetyNumberRecipient>>> {
    val recipientObservable = getResolvedIdentities(recipients)
    val distributionListObservable = getDistributionLists(destinations)
    val groupObservable = getGroups(destinations)
    val destinationRecipientIds = destinations.map { it.recipientId }.toSet()

    return Observable.combineLatest(recipientObservable, distributionListObservable, groupObservable) { recipientList, distributionLists, groups ->
      val map = mutableMapOf<SafetyNumberBucket, List<SafetyNumberRecipient>>()

      recipientList.forEach {
        val distributionListMemberships = getDistributionMemberships(it.recipient, distributionLists)
        val groupMemberships = getGroupMemberships(it.recipient, groups)
        val isInContactsBucket = destinationRecipientIds.contains(it.recipient.id)

        val safetyNumberRecipient = SafetyNumberRecipient(
          it.recipient,
          it.identityRecord.get(),
          distributionListMemberships.size,
          groupMemberships.size
        )

        distributionListMemberships.forEach { distributionListRecord ->
          insert(map, SafetyNumberBucket.DistributionListBucket(distributionListRecord.id, distributionListRecord.name), safetyNumberRecipient)
        }

        groupMemberships.forEach { group ->
          insert(map, SafetyNumberBucket.GroupBucket(group), safetyNumberRecipient)
        }

        if (isInContactsBucket) {
          insert(map, SafetyNumberBucket.ContactsBucket, safetyNumberRecipient)
        }
      }

      map.toMap()
    }.toFlowable(BackpressureStrategy.LATEST).subscribeOn(Schedulers.io())
  }

  /**
   * Removes the given recipient from all stories they're a member of in destinations.
   */
  fun removeFromStories(recipientId: RecipientId, destinations: List<ContactSearchKey.RecipientSearchKey>): Completable {
    return filterForDistributionLists(destinations).flatMapCompletable { distributionRecipients ->
      Completable.fromAction {
        distributionRecipients
          .mapNotNull { SignalDatabase.distributionLists.getList(it.requireDistributionListId()) }
          .filter { it.members.contains(recipientId) }
          .forEach {
            SignalDatabase.distributionLists.excludeFromStory(recipientId, it)
            Stories.onStorySettingsChanged(it.id)
          }
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Removes the given set of recipients from the specified distribution list
   */
  fun removeAllFromStory(recipientIds: List<RecipientId>, distributionList: DistributionListId): Completable {
    return Completable.fromAction {
      val record = SignalDatabase.distributionLists.getList(distributionList)
      if (record != null) {
        SignalDatabase.distributionLists.excludeAllFromStory(recipientIds, record)
        Stories.onStorySettingsChanged(distributionList)
      }
    }.subscribeOn(Schedulers.io())
  }

  private fun insert(map: MutableMap<SafetyNumberBucket, List<SafetyNumberRecipient>>, bucket: SafetyNumberBucket, safetyNumberRecipient: SafetyNumberRecipient) {
    val bucketList = map.getOrDefault(bucket, emptyList())
    map[bucket] = bucketList + safetyNumberRecipient
  }

  private fun filterForDistributionLists(destinations: List<ContactSearchKey.RecipientSearchKey>): Single<List<Recipient>> {
    return Single.fromCallable {
      val recipients = Recipient.resolvedList(destinations.map { it.recipientId })
      recipients.filter { it.isDistributionList }
    }
  }

  private fun filterForGroups(destinations: List<ContactSearchKey.RecipientSearchKey>): Single<List<Recipient>> {
    return Single.fromCallable {
      val recipients = Recipient.resolvedList(destinations.map { it.recipientId })
      recipients.filter { it.isGroup }
    }
  }

  private fun observeDistributionList(recipient: Recipient): Observable<DistributionListRecord> {
    return Recipient.observable(recipient.id).map { SignalDatabase.distributionLists.getList(it.requireDistributionListId())!! }
  }

  private fun getDistributionLists(destinations: List<ContactSearchKey.RecipientSearchKey>): Observable<List<DistributionListRecord>> {
    val distributionListRecipients: Single<List<Recipient>> = filterForDistributionLists(destinations)

    return distributionListRecipients.flatMapObservable { recipients ->
      if (recipients.isEmpty()) {
        Observable.just(emptyList())
      } else {
        val distributionListObservables = recipients.map { observeDistributionList(it) }
        Observable.combineLatest(distributionListObservables) {
          it.filterIsInstance<DistributionListRecord>()
        }
      }
    }
  }

  private fun getGroups(destinations: List<ContactSearchKey.RecipientSearchKey>): Observable<List<Recipient>> {
    val groupRecipients: Single<List<Recipient>> = filterForGroups(destinations)
    return groupRecipients.flatMapObservable { recipients ->
      if (recipients.isEmpty()) {
        Observable.just(emptyList())
      } else {
        val recipientObservables = recipients.map {
          Recipient.observable(it.id)
        }
        Observable.combineLatest(recipientObservables) {
          it.filterIsInstance<Recipient>()
        }
      }
    }
  }

  private fun getResolvedIdentities(recipients: List<RecipientId>): Observable<List<ResolvedIdentity>> {
    val recipientObservables: List<Observable<ResolvedIdentity>> = recipients.map {
      Recipient.observable(it).switchMap { recipient ->
        Observable.fromCallable {
          val record = AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipient.id)
          ResolvedIdentity(recipient, record)
        }
      }
    }

    return Observable.combineLatest(recipientObservables) { identities ->
      identities.filterIsInstance<ResolvedIdentity>().filter { it.identityRecord.isPresent }
    }
  }

  private fun getDistributionMemberships(recipient: Recipient, distributionLists: List<DistributionListRecord>): List<DistributionListRecord> {
    return distributionLists.filter { it.members.contains(recipient.id) }
  }

  private fun getGroupMemberships(recipient: Recipient, groups: List<Recipient>): List<Recipient> {
    return groups.filter { it.participantIds.contains(recipient.id) }
  }

  data class ResolvedIdentity(val recipient: Recipient, val identityRecord: Optional<IdentityRecord>)
}
