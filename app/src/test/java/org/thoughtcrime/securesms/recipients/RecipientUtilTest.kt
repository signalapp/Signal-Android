package org.thoughtcrime.securesms.recipients

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
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.GroupRecord

class RecipientUtilTest {
  private val recipient = mockk<Recipient>(relaxed = true)
  private val mockThreadTable = mockk<ThreadTable>(relaxed = true)
  private val mockMessageTable = mockk<MessageTable>()
  private val mockRecipientTable = mockk<RecipientTable>()
  private val mockGroupTable = mockk<GroupTable>()
  private val self = mockk<Recipient>(relaxed = true)
  private val groupRecipientId = RecipientId.from(6)
  private val groupRecipient = mockk<Recipient>(relaxed = true)
  private val groupRecord = mockk<GroupRecord>()

  @Before
  fun setUp() {
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.instance } returns mockk {
      every { threadTable } returns mockThreadTable
      every { messageTable } returns mockMessageTable
      every { recipientTable } returns mockRecipientTable
      every { groupTable } returns mockGroupTable
    }

    every { recipient.id } returns RecipientId.from(5)
    every { recipient.resolve() } returns recipient

    mockkObject(Recipient)
    every { Recipient.self() } returns self
    every { Recipient.resolved(groupRecipientId) } returns groupRecipient

    every { groupRecipient.id } returns groupRecipientId
    every { groupRecord.hasV2GroupProperties } returns true
    every { groupRecord.recipientId } returns groupRecipientId
    every { groupRecord.memberLevel(self) } returns GroupTable.MemberLevel.FULL_MEMBER
    every { mockGroupTable.getPushGroupsContainingMember(recipient.id) } returns listOf(groupRecord)
  }

  @After
  fun cleanup() {
    unmockkObject(Recipient)
    unmockkObject(SignalDatabase.Companion)
  }

  @Test
  fun givenThreadIsNegativeOne_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(-1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsNullForThreadId_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIHaveSentASecureMessageInThisThread_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockThreadTable.getRecipientForThreadId(any()) } returns recipient
    every { mockMessageTable.getOutgoingSecureMessageCount(1L) } returns 5

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(1L)

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
    val result = RecipientUtil.isMessageRequestAccepted(1L)

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
    val result = RecipientUtil.isMessageRequestAccepted(1L)

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
    val result = RecipientUtil.isMessageRequestAccepted(1L)

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
    val result = RecipientUtil.isMessageRequestAccepted(1L)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsNull_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(null)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenNonZeroOutgoingSecureMessageCount_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 1

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenIAmProfileSharing_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsASystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isSystemContact } returns true

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(recipient)

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
    val result = RecipientUtil.isMessageRequestAccepted(recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenNoSecureMessagesSentNoSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { mockMessageTable.getSecureMessageCount(any()) } returns 0

    // WHEN
    val result = RecipientUtil.isMessageRequestAccepted(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenNonZeroOutgoingSecureMessageCountAndRecipientIsHidden_whenIsCallRequestAccepted_thenIExpectFalse() {
    // GIVEN
    every { recipient.isHidden } returns true
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 1

    // WHEN
    val result = RecipientUtil.isCallRequestAccepted(recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenNonZeroOutgoingSecureMessageCountAndRecipientIsNotHidden_whenIsCallRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isHidden } returns false
    every { mockMessageTable.getOutgoingSecureMessageCount(any()) } returns 1

    // WHEN
    val result = RecipientUtil.isCallRequestAccepted(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsHiddenAndProfileSharing_whenIsCallRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isHidden } returns true
    every { recipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.isCallRequestAccepted(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenRecipientIsHiddenAndSystemContact_whenIsCallRequestAccepted_thenIExpectTrue() {
    // GIVEN
    every { recipient.isHidden } returns true
    every { recipient.isSystemContact } returns true

    // WHEN
    val result = RecipientUtil.isCallRequestAccepted(recipient)

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

  @Test
  fun givenRecipientIsBlocked_whenShouldHaveProfileKey_thenIExpectFalse() {
    // GIVEN
    every { recipient.isBlocked } returns true
    every { recipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenRecipientIsProfileSharing_whenShouldHaveProfileKey_thenIExpectTrue() {
    // GIVEN
    every { recipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenSharedGv2GroupWithProfileSharing_whenShouldHaveProfileKey_thenIExpectTrue() {
    // GIVEN
    every { recipient.isProfileSharing } returns false
    every { groupRecipient.isProfileSharing } returns true

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenSharedGv2GroupWithoutProfileSharing_whenShouldHaveProfileKey_thenIExpectFalse() {
    // GIVEN
    every { recipient.isProfileSharing } returns false
    every { groupRecipient.isProfileSharing } returns false

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenSharedGv2GroupIsBlocked_whenShouldHaveProfileKey_thenIExpectFalse() {
    // GIVEN
    every { recipient.isProfileSharing } returns false
    every { groupRecipient.isProfileSharing } returns true
    every { groupRecipient.isBlocked } returns true

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenSharedGv2GroupWhereIAmPending_whenShouldHaveProfileKey_thenIExpectFalse() {
    // GIVEN
    every { recipient.isProfileSharing } returns false
    every { groupRecipient.isProfileSharing } returns true
    every { groupRecord.memberLevel(self) } returns GroupTable.MemberLevel.PENDING_MEMBER

    // WHEN
    val result = RecipientUtil.shouldHaveProfileKey(recipient)

    // THEN
    assertFalse(result)
  }
}
