/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingParticipantCollectionTest {

  private val fakeNowProvider = FakeNowProvider()
  private val testSubject = PendingParticipantCollection(
    participantMap = emptyMap(),
    nowProvider = fakeNowProvider
  )

  @Test
  fun `Given an empty collection, when I getUnresolvedPendingParticipants, then I expect an empty set`() {
    val expected = emptySet<PendingParticipantCollection.Entry>()
    val actual = testSubject.getUnresolvedPendingParticipants()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given an empty collection, when I getAllPendingParticipants, then I expect an empty set`() {
    val expected = emptySet<PendingParticipantCollection.Entry>()
    val actual = testSubject.getAllPendingParticipants(0.milliseconds)

    assertEquals(expected, actual)
  }

  @Test
  fun `Given an empty collection, when I withRecipients, then I expect all recipients to be added`() {
    val recipients = createRecipients(10)
    val expected = (0 until 10)
      .map {
        PendingParticipantCollection.Entry(
          recipient = recipients[it],
          state = PendingParticipantCollection.State.PENDING,
          stateChangeAt = 0.milliseconds
        )
      }
      .toSet()

    val actual = testSubject.withRecipients(recipients).getUnresolvedPendingParticipants()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a collection with 10 PENDING entries, when I getAllPendingParticipants, then I expect all entries`() {
    val recipients = createRecipients(10)
    val expected = (0 until 10)
      .map {
        PendingParticipantCollection.Entry(
          recipient = recipients[it],
          state = PendingParticipantCollection.State.PENDING,
          stateChangeAt = 0.milliseconds
        )
      }
      .toSet()

    val actual = testSubject.withRecipients(recipients).getAllPendingParticipants(0.milliseconds)

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a collection with 10 PENDING entries and duration in the future, when I getAllPendingParticipants, then I expect all entries`() {
    val recipients = createRecipients(10)
    val expected = (0 until 10)
      .map {
        PendingParticipantCollection.Entry(
          recipient = recipients[it],
          state = PendingParticipantCollection.State.PENDING,
          stateChangeAt = 0.milliseconds
        )
      }
      .toSet()

    val actual = testSubject.withRecipients(recipients).getAllPendingParticipants(1.milliseconds)

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a collection with 10 PENDING entries and duration in the past, when I getAllPendingParticipants, then I expect all entries`() {
    fakeNowProvider.invoke()
    val recipients = createRecipients(10)
    val expected = (0 until 10)
      .map {
        PendingParticipantCollection.Entry(
          recipient = recipients[it],
          state = PendingParticipantCollection.State.PENDING,
          stateChangeAt = 1.milliseconds
        )
      }
      .toSet()

    val actual = testSubject.withRecipients(recipients).getAllPendingParticipants(0.milliseconds)

    assertEquals(expected, actual)
  }

  @Test
  fun `Given an approved recipient with a state change after since, when I getAllPendingParticipants, then I expect approved recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withApproval(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[0],
      state = PendingParticipantCollection.State.APPROVED,
      stateChangeAt = 1.milliseconds
    )
    val actual = subject.getAllPendingParticipants(0.milliseconds).first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given an approved recipient with a state change before since, when I getAllPendingParticipants, then I do not expect the approved recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withApproval(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[1],
      state = PendingParticipantCollection.State.PENDING,
      stateChangeAt = 0.milliseconds
    )
    val actual = subject.getAllPendingParticipants(2.milliseconds).first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given an approved recipient, when I getUnresolvedPendingParticipants, then I do not expect the approved recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withApproval(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[1],
      state = PendingParticipantCollection.State.PENDING,
      stateChangeAt = 0.milliseconds
    )
    val actual = subject.getUnresolvedPendingParticipants().first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a denied recipient with a state change after since, when I getAllPendingParticipants, then I expect denied recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withDenial(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[0],
      state = PendingParticipantCollection.State.DENIED,
      stateChangeAt = 1.milliseconds,
      denialCount = 1
    )
    val actual = subject.getAllPendingParticipants(0.milliseconds).first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a denied recipient with a state change before since, when I getAllPendingParticipants, then I do not expect the denied recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withDenial(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[1],
      state = PendingParticipantCollection.State.PENDING,
      stateChangeAt = 0.milliseconds
    )
    val actual = subject.getAllPendingParticipants(2.milliseconds).first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a denied recipient, when I getUnresolvedPendingParticipants, then I do not expect the denied recipient`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withDenial(recipients[0])
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[1],
      state = PendingParticipantCollection.State.PENDING,
      stateChangeAt = 0.milliseconds
    )
    val actual = subject.getUnresolvedPendingParticipants().first()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a list of PENDING, when I withRecipients with empty list, then I clear the collection`() {
    val recipients = createRecipients(10)
    val subject = testSubject.withRecipients(recipients).withRecipients(emptyList())
    val expected = emptySet<PendingParticipantCollection.Entry>()
    val actual = subject.getUnresolvedPendingParticipants()

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a mixed list, when I withRecipients with empty list, then I clear the PENDINGs`() {
    val recipients = createRecipients(2)
    val subject = testSubject.withRecipients(recipients).withApproval(recipients[0]).withRecipients(emptyList())
    val expected = setOf(
      PendingParticipantCollection.Entry(
        recipient = recipients[0],
        state = PendingParticipantCollection.State.APPROVED,
        stateChangeAt = 1.milliseconds
      )
    )
    val actual = subject.getAllPendingParticipants(0.milliseconds)

    assertEquals(expected, actual)
  }

  @Test
  fun `Given a participant is denied once, when I withRecipients, then I expect the state to be changed to PENDING`() {
    val recipients = createRecipients(1)
    val expected = PendingParticipantCollection.Entry(
      recipient = recipients[0],
      state = PendingParticipantCollection.State.PENDING,
      stateChangeAt = 2.milliseconds,
      denialCount = 1
    )

    val actual = testSubject
      .withRecipients(recipients)
      .withDenial(recipients[0])
      .withRecipients(recipients)
      .getAllPendingParticipants(0.milliseconds)

    assertEquals(expected, actual.first())
  }

  private fun createRecipients(count: Int): List<Recipient> {
    return (1..count).map { RecipientDatabaseTestUtils.createRecipient() }
  }

  private class FakeNowProvider : () -> Duration {

    private val nowIterator = (0 until 500).iterator()

    override fun invoke(): Duration {
      return nowIterator.next().milliseconds
    }
  }
}
