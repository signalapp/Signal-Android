package org.thoughtcrime.securesms.safety

import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule

class SafetyNumberBottomSheetRepositoryTest {

  @get:Rule val harness = SignalActivityRule(othersCount = 10)

  private val testScheduler = TestScheduler()
  private val subjectUnderTest = SafetyNumberBottomSheetRepository()

  @Before
  fun setUp() {
    RxJavaPlugins.setInitIoSchedulerHandler { testScheduler }
    RxJavaPlugins.setIoSchedulerHandler { testScheduler }
  }

  @Test
  fun givenIOnlyHave1to1Destinations_whenIGetBuckets_thenIOnlyHaveContactsBucketContainingAllRecipients() {
    val recipients = harness.others
    val destinations = harness.others.map { ContactSearchKey.RecipientSearchKey.KnownRecipient(it) }

    val result = subjectUnderTest.getBuckets(recipients, destinations).test()

    testScheduler.triggerActions()

    result.assertValueAt(0) { map ->
      assertMatch(map, mapOf(SafetyNumberBucket.ContactsBucket to harness.others))
    }
  }

  @Test
  fun givenIOnlyHaveASingle1to1Destination_whenIGetBuckets_thenIOnlyHaveContactsBucketContainingAllRecipients() {
    // GIVEN
    val recipients = harness.others
    val destination = harness.others.take(1).map { ContactSearchKey.RecipientSearchKey.KnownRecipient(it) }

    // WHEN
    val result = subjectUnderTest.getBuckets(recipients, destination).test(1)
    testScheduler.triggerActions()

    // THEN
    result.assertValue { map ->
      assertMatch(map, mapOf(SafetyNumberBucket.ContactsBucket to harness.others.take(1)))
    }
  }

  @Test
  fun givenIHaveADistributionListDestination_whenIGetBuckets_thenIOnlyHaveDistributionListDestinationWithCorrespondingMembers() {
    // GIVEN
    val distributionListMembers = harness.others.take(5)
    val distributionList = SignalDatabase.distributionLists.createList("ListA", distributionListMembers)!!
    val destinationKey = ContactSearchKey.RecipientSearchKey.Story(SignalDatabase.distributionLists.getRecipientId(distributionList)!!)

    // WHEN
    val result = subjectUnderTest.getBuckets(harness.others, listOf(destinationKey)).test(1)
    testScheduler.triggerActions()

    // THEN
    result.assertValue { map ->
      assertMatch(
        map,
        mapOf(
          SafetyNumberBucket.DistributionListBucket(distributionList, "ListA") to harness.others.take(5)
        )
      )
    }
  }

  @Test
  fun givenIHaveADistributionListDestinationAndIGetBuckets_whenIRemoveFromStories_thenIOnlyHaveDistributionListDestinationWithCorrespondingMembers() {
    // GIVEN
    val distributionListMembers = harness.others.take(5)
    val toRemove = distributionListMembers.last()
    val distributionList = SignalDatabase.distributionLists.createList("ListA", distributionListMembers)!!
    val destinationKey = ContactSearchKey.RecipientSearchKey.Story(SignalDatabase.distributionLists.getRecipientId(distributionList)!!)
    val testSubscriber = subjectUnderTest.getBuckets(distributionListMembers, listOf(destinationKey)).test(2)
    testScheduler.triggerActions()

    // WHEN
    subjectUnderTest.removeFromStories(toRemove, listOf(destinationKey)).subscribe()
    testSubscriber.request(1)
    testScheduler.triggerActions()
    testSubscriber.awaitCount(3)

    // THEN
    testSubscriber.assertValueAt(2) { map ->
      assertMatch(
        map,
        mapOf(
          SafetyNumberBucket.DistributionListBucket(distributionList, "ListA") to distributionListMembers.dropLast(1)
        )
      )
    }
  }

  @Test
  fun givenIHaveADistributionListDestinationAndIGetBuckets_whenIRemoveAllFromStory_thenINoLongerHaveEntryForThatBucket() {
    // GIVEN
    val distributionListMembers = harness.others.take(5)
    val distributionList = SignalDatabase.distributionLists.createList("ListA", distributionListMembers)!!
    val destinationKey = ContactSearchKey.RecipientSearchKey.Story(SignalDatabase.distributionLists.getRecipientId(distributionList)!!)
    val testSubscriber = subjectUnderTest.getBuckets(distributionListMembers, listOf(destinationKey)).test(2)
    testScheduler.triggerActions()

    // WHEN
    subjectUnderTest.removeAllFromStory(distributionListMembers, distributionList).subscribe()
    testSubscriber.request(1)
    testScheduler.triggerActions()
    testSubscriber.awaitCount(3)

    // THEN
    testSubscriber.assertValueAt(2) { map ->
      assertMatch(map, mapOf())
    }
  }

  private fun assertMatch(
    resultMap: Map<SafetyNumberBucket, List<SafetyNumberRecipient>>,
    idMap: Map<SafetyNumberBucket, List<RecipientId>>
  ): Boolean {
    assertEquals("Result and ID Maps had different key sets", idMap.keys, resultMap.keys)

    resultMap.forEach { (bucket, members) ->
      assertEquals("Mismatch in Bucket $bucket", idMap[bucket], members.map { it.recipient.id })
    }

    return true
  }
}
