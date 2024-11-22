package org.thoughtcrime.securesms.recipients

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable

class RecipientUtilTest {
  private val context = mockk<Context>()
  private val recipient = mockk<Recipient>(relaxed = true)
  private val mockThreadTable = mockk<ThreadTable>(relaxed = true)
  private val mockMessageTable = mockk<MessageTable>()
  private val mockRecipientTable = mockk<RecipientTable>()

  @Before
  fun setUp() {
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.Companion.instance } returns mockk {
      every { threadTable } returns mockThreadTable
      every { messageTable } returns mockMessageTable
      every { recipientTable } returns mockRecipientTable
    }

    every { recipient.id } returns RecipientId.from(5)
    every { recipient.resolve() } returns recipient
  }

  @After
  fun cleanup() {
    unmockkObject(SignalDatabase.Companion)
  }

  @Test
  fun givenThreadIsNegativeOne_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, -1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsNullForThreadId_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIHaveSentASecureMessageInThisThread_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 5

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIHaveNotSentASecureMessageInThisThreadAndIAmProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isProfileSharing } returns true
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 0

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIHaveNotSentASecureMessageInThisThreadAndRecipientIsSystemContact_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isSystemContact } returns true
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 0

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertTrue(result)
  }

  @Ignore
  @Test
  fun givenIHaveReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectFalse() {
    // GIVEN
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 0
    every { mockMessageTable.getSecureMessageCount(1L) } returns 5

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenIHaveNotReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 0
    every { mockMessageTable.getSecureMessageCount(1L) } returns 0

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, 1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsNull_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, null)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenNonZeroOutgoingSecureMessageCount_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 1

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIAmProfileSharing_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsASystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isSystemContact } returns true

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, recipient)

    // THEN
    assertTrue(result)
  }

  @Ignore
  @Test
  fun givenNoSecureMessagesSentSomeSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectFalse() {
    // GIVEN
    every { recipient.isRegistered } returns true
    every { mockMessageTable.getSecureMessageCount(any()) } returns 5

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenNoSecureMessagesSentNoSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockMessageTable.getSecureMessageCount(any()) } returns 0

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(context, recipient)

    // THEN
    assertTrue(result)
  }

  @Ignore
  @Test
  fun givenNoSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 0

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(recipient)

    // THEN
    verify { mockRecipientTable.setProfileSharing(recipient.id, true) }
  }

  @Ignore
  @Test
  fun givenSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 5

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(recipient)

    // THEN
    verify(exactly = 0) { mockRecipientTable.setProfileSharing(recipient.id, true) }
  }
}
