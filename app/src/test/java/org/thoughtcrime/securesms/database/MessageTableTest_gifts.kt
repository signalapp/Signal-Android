package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_gifts {

  @get:Rule
  val recipientTestRule = RecipientTestRule()

  private lateinit var mms: MessageTable

  private lateinit var recipients: List<RecipientId>

  @Before
  fun setUp() {
    mms = SignalDatabase.messages

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
