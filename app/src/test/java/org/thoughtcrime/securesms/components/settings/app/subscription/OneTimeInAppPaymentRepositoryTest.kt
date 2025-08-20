package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.RxPluginsRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class OneTimeInAppPaymentRepositoryTest {

  @get:Rule
  val rxRule = RxPluginsRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  @Test
  fun `Given a throwable and self, when I handleCreatePaymentIntentError, then I expect a ONE_TIME error`() {
    val throwable = Exception()
    val selfId = RecipientId.from(1)
    val self = Recipient(
      id = selfId,
      isSelf = true
    )

    mockkStatic(Recipient::class)
    every { Recipient.resolved(selfId) } returns self

    val testObserver = OneTimeInAppPaymentRepository.handleCreatePaymentIntentError<Unit>(throwable, selfId, PaymentSourceType.Stripe.CreditCard).test()
    rxRule.defaultScheduler.triggerActions()

    testObserver.assertError {
      it is DonationError && it.source == DonationErrorSource.ONE_TIME
    }
  }

  @Test
  fun `Given a throwable and not self, when I handleCreatePaymentIntentError, then I expect a GIFT error`() {
    val throwable = Exception()
    val otherId = RecipientId.from(1)
    val other = Recipient(
      id = otherId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED
    )

    mockkStatic(Recipient::class)
    every { Recipient.resolved(otherId) } returns other

    val testObserver = OneTimeInAppPaymentRepository.handleCreatePaymentIntentError<Unit>(throwable, otherId, PaymentSourceType.Stripe.CreditCard).test()
    rxRule.defaultScheduler.triggerActions()

    testObserver.assertError {
      it is DonationError && it.source == DonationErrorSource.GIFT
    }
  }

  @Test
  fun `Given a registered non-self individual, when I verifyRecipientIsAllowedToReceiveAGift, then I expect no error`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED
    )

    mockkStatic(Recipient::class)
    every { Recipient.resolved(recipientId) } returns recipient

    OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGiftSync(recipientId)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given self, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = true
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given an unregistered individual, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.NOT_REGISTERED
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given a group, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      groupIdValue = mockk()
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given a call link, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      callLinkRoomId = CallLinkRoomId.fromBytes(byteArrayOf())
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given a distribution list, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      distributionListIdValue = DistributionListId.from(1L)
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test(expected = DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid::class)
  fun `Given release notes, when I verifyRecipientIsAllowedToReceiveAGift, then I expect SelectedRecipientIsInvalid`() {
    val recipientId = RecipientId.from(1L)
    val recipient = Recipient(
      id = recipientId,
      isSelf = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      isReleaseNotes = true
    )

    verifyRecipientIsNotAllowedToBeGiftedBadges(recipient)
  }

  @Test
  fun `When I getBoosts, then I expect a filtered set of boost objects`() {
    inAppPaymentsTestRule.initializeDonationsConfigurationMock()

    val testObserver = OneTimeInAppPaymentRepository.getBoosts().test()
    rxRule.defaultScheduler.triggerActions()

    testObserver
      .assertValue {
        it.size == 3
      }
      .assertComplete()
  }

  @Test
  fun `When I getBoostBadge, then I expect a boost badge`() {
    inAppPaymentsTestRule.initializeDonationsConfigurationMock()

    val testObserver = OneTimeInAppPaymentRepository.getBoostBadge().test()
    rxRule.defaultScheduler.triggerActions()

    testObserver
      .assertValue { it.isBoost() }
      .assertComplete()
  }

  @Test
  fun `When I getMinimumDonationAmounts, then I expect a map of 3 currencies`() {
    inAppPaymentsTestRule.initializeDonationsConfigurationMock()

    val testObserver = OneTimeInAppPaymentRepository.getMinimumDonationAmounts().test()
    rxRule.defaultScheduler.triggerActions()

    testObserver
      .assertValue { it.size == 3 }
      .assertComplete()
  }

  private fun verifyRecipientIsNotAllowedToBeGiftedBadges(recipient: Recipient) {
    mockkStatic(Recipient::class)
    every { Recipient.resolved(recipient.id) } returns recipient

    OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGiftSync(recipient.id)
  }
}
