package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.components.settings.app.subscription.PayPalRepository

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PayPalConfirmationResultTest {

  companion object {
    private val PAYER_ID = "asdf"
    private val PAYMENT_ID = "sdfg"
    private val PAYMENT_TOKEN = "dfgh"

    private val TEST_URL = "${PayPalRepository.ONE_TIME_RETURN_URL}?PayerID=$PAYER_ID&paymentId=$PAYMENT_ID&token=$PAYMENT_TOKEN"
    private val TEST_MISSING_PARAM_URL = "${PayPalRepository.ONE_TIME_RETURN_URL}?paymentId=$PAYMENT_ID&token=$PAYMENT_TOKEN"
  }

  @Test
  fun givenATestUrl_whenIFromUri_thenIExpectCorrectResult() {
    val result = PayPalConfirmationResult.fromUrl(TEST_URL)

    assertEquals(
      PayPalConfirmationResult(PAYER_ID, PAYMENT_ID, PAYMENT_TOKEN),
      result
    )
  }

  @Test
  fun givenATestUrlWithMissingField_whenIFromUri_thenIExpectNull() {
    val result = PayPalConfirmationResult.fromUrl(TEST_MISSING_PARAM_URL)

    assertNull(result)
  }
}
