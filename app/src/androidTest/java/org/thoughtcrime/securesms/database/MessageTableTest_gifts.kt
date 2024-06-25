package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class MessageTableTest_gifts {
  private lateinit var mms: MessageTable

  private val localAci = ACI.from(UUID.randomUUID())
  private val localPni = PNI.from(UUID.randomUUID())

  private lateinit var recipients: List<RecipientId>

  @Before
  fun setUp() {
    mms = SignalDatabase.messages

    mms.deleteAllThreads()

    SignalStore.account.setAci(localAci)
    SignalStore.account.setPni(localPni)

    recipients = (0 until 5).map { SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID())) }
  }

  @Test
  fun givenNoSentGifts_whenISetOutgoingGiftsRevealed_thenIExpectEmptyList() {
    val result = mms.setOutgoingGiftsRevealed(listOf(1))

    assertTrue(result.isEmpty())
  }

  @Test
  fun givenSentGift_whenISetOutgoingGiftsRevealed_thenIExpectNonEmptyListContainingThatGift() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId))

    assertTrue(result.isNotEmpty())
    assertEquals(messageId, result.first().messageId.id)
  }

  @Test
  fun givenViewedSentGift_whenISetOutgoingGiftsRevealed_thenIExpectEmptyList() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )
    mms.setOutgoingGiftsRevealed(listOf(messageId))

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId))

    assertTrue(result.isEmpty())
  }

  @Test
  fun givenMultipleSentGift_whenISetOutgoingGiftsRevealedForOne_thenIExpectNonEmptyListContainingThatGift() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 2,
      giftBadge = GiftBadge()
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId))

    assertEquals(1, result.size)
    assertEquals(messageId, result.first().messageId.id)
  }

  @Test
  fun givenMultipleSentGift_whenISetOutgoingGiftsRevealedForBoth_thenIExpectNonEmptyListContainingThoseGifts() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    val messageId2 = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 2,
      giftBadge = GiftBadge()
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId, messageId2))

    assertEquals(listOf(messageId, messageId2), result.map { it.messageId.id })
  }

  @Test
  fun givenMultipleSentGiftAndNonGift_whenISetOutgoingGiftsRevealedForBothGifts_thenIExpectNonEmptyListContainingJustThoseGifts() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    val messageId2 = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 2,
      giftBadge = GiftBadge()
    )

    MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 3,
      giftBadge = null
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId, messageId2))

    assertEquals(listOf(messageId, messageId2), result.map { it.messageId.id })
  }

  @Test
  fun givenMultipleSentGiftAndNonGift_whenISetOutgoingGiftsRevealedForAllThree_thenIExpectNonEmptyListContainingJustThoseGifts() {
    val messageId = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    val messageId2 = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 2,
      giftBadge = GiftBadge()
    )

    val messageId3 = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 3,
      giftBadge = null
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId, messageId2, messageId3))

    assertEquals(listOf(messageId, messageId2), result.map { it.messageId.id })
  }

  @Test
  fun givenMultipleSentGiftAndNonGift_whenISetOutgoingGiftsRevealedForNonGift_thenIExpectEmptyList() {
    MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 1,
      giftBadge = GiftBadge()
    )

    MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 2,
      giftBadge = GiftBadge()
    )

    val messageId3 = MmsHelper.insert(
      recipient = Recipient.resolved(recipients[0]),
      sentTimeMillis = 3,
      giftBadge = null
    )

    val result = mms.setOutgoingGiftsRevealed(listOf(messageId3))

    assertTrue(result.isEmpty())
  }
}
