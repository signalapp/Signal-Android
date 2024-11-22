package org.whispersystems.signalservice.api.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.PushServiceSocket

class DonationsServiceTest {
  private val pushServiceSocket: PushServiceSocket = mockk<PushServiceSocket>()
  private val testSubject = DonationsService(pushServiceSocket)
  private val activeSubscription = ActiveSubscription.EMPTY

  @Test
  fun givenASubscriberId_whenIGetASuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndNonEmptyObject() {
    // GIVEN
    val subscriberId = SubscriberId.generate()
    every { pushServiceSocket.getSubscription(subscriberId.serialize()) } returns activeSubscription

    // WHEN
    val response = testSubject.getSubscription(subscriberId)

    // THEN
    verify { pushServiceSocket.getSubscription(subscriberId.serialize()) }
    assertEquals(200, response.status)
    assertTrue(response.result.isPresent)
  }

  @Test
  fun givenASubscriberId_whenIGetAnUnsuccessfulResponse_thenItIsMappedWithTheCorrectStatusCodeAndEmptyObject() {
    // GIVEN
    val subscriberId = SubscriberId.generate()
    every { pushServiceSocket.getSubscription(subscriberId.serialize()) } throws NonSuccessfulResponseCodeException(403)

    // WHEN
    val response = testSubject.getSubscription(subscriberId)

    // THEN
    verify { pushServiceSocket.getSubscription(subscriberId.serialize()) }
    assertEquals(403, response.status)
    assertFalse(response.result.isPresent)
  }
}
