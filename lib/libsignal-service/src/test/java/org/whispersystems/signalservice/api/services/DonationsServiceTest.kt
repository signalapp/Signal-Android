package org.whispersystems.signalservice.api.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.rest.RestStatusCodeError
import org.whispersystems.signalservice.api.donations.DonationPermitProvider
import org.whispersystems.signalservice.api.donations.DonationsApi
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

class DonationsServiceTest {
  private val donationsApi: DonationsApi = mockk<DonationsApi>()
  private val testSubject = DonationsService(donationsApi, DonationPermitProvider { RequestResult.Success("permit") })
  private val activeSubscription = ActiveSubscription.EMPTY

  private fun serviceWithPermit(permit: String) = DonationsService(donationsApi, DonationPermitProvider { RequestResult.Success(permit) })

  private fun serviceWithPermitResult(result: RequestResult<String, RestStatusCodeError>) = DonationsService(donationsApi, DonationPermitProvider { result })

  @Test
  fun givenASubscriberId_whenIGetASuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndNonEmptyObject() {
    // GIVEN
    val subscriberId = SubscriberId.generate()
    every { donationsApi.getSubscription(subscriberId) } returns NetworkResult.Success(activeSubscription)

    // WHEN
    val response = testSubject.getSubscription(subscriberId)

    // THEN
    verify { donationsApi.getSubscription(subscriberId) }
    assertEquals(200, response.status)
    assertTrue(response.result.isPresent)
  }

  @Test
  fun givenASubscriberId_whenIGetAnUnsuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndEmptyObject() {
    // GIVEN
    val subscriberId = SubscriberId.generate()
    every { donationsApi.getSubscription(subscriberId) } returns NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(403))

    // WHEN
    val response = testSubject.getSubscription(subscriberId)

    // THEN
    verify { donationsApi.getSubscription(subscriberId) }
    assertEquals(403, response.status)
    assertFalse(response.result.isPresent)
  }

  @Test
  fun givenAPermitProvider_whenICreateDonationIntent_thenTheProvidersPermitIsForwarded() {
    every { donationsApi.createStripeOneTimePaymentIntent(any(), any(), any(), any(), any()) } returns NetworkResult.Success(mockk<StripeClientSecret>())

    serviceWithPermit("permit-x").createDonationIntentWithAmount("500", "USD", 1, "CARD")

    verify { donationsApi.createStripeOneTimePaymentIntent("USD", "CARD", 500, 1, "permit-x") }
  }

  @Test
  fun givenAPermitProvider_whenICreateStripeSubscriptionPaymentMethod_thenTheProvidersPermitIsForwarded() {
    val subscriberId = SubscriberId.generate()
    every { donationsApi.createStripeSubscriptionPaymentMethod(any(), any(), any()) } returns NetworkResult.Success(mockk<StripeClientSecret>())

    serviceWithPermit("permit-x").createStripeSubscriptionPaymentMethod(subscriberId, "CARD")

    verify { donationsApi.createStripeSubscriptionPaymentMethod(subscriberId, "CARD", "permit-x") }
  }

  @Test
  fun givenAPermitProvider_whenICreateSubscriber_thenTheProvidersPermitIsForwarded() {
    val subscriberId = SubscriberId.generate()
    every { donationsApi.createSubscriber(any(), any()) } returns NetworkResult.Success(Unit)

    serviceWithPermit("permit-x").createSubscriber(subscriberId)

    verify { donationsApi.createSubscriber(subscriberId, "permit-x") }
  }

  @Test
  fun givenAFailingPermitProvider_whenICreateSubscriber_thenItFailsAndNoSubscriberIsCreated() {
    val subscriberId = SubscriberId.generate()
    every { donationsApi.createSubscriber(any(), any()) } returns NetworkResult.Success(Unit)

    val response = serviceWithPermitResult(RequestResult.NonSuccess(RestStatusCodeError(429, emptyMap(), null))).createSubscriber(subscriberId)

    assertEquals(429, response.status)
    verify(exactly = 0) { donationsApi.createSubscriber(any(), any()) }
  }

  @Test
  fun whenIPutSubscription_thenNoPermitIsEverAttached() {
    val subscriberId = SubscriberId.generate()
    every { donationsApi.putSubscription(any()) } returns NetworkResult.Success(Unit)

    serviceWithPermit("permit-x").putSubscription(subscriberId)

    verify { donationsApi.putSubscription(subscriberId) }
    verify(exactly = 0) { donationsApi.createSubscriber(any(), any()) }
  }
}
