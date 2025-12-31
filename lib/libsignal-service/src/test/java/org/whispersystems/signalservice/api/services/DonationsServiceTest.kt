package org.whispersystems.signalservice.api.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.donations.DonationsApi
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

class DonationsServiceTest {
  private val donationsApi: DonationsApi = mockk<DonationsApi>()
  private val testSubject = DonationsService(donationsApi)
  private val activeSubscription = ActiveSubscription.EMPTY

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
}
