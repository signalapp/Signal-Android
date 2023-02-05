package org.signal.donations

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StripeIntentAccessorTest {

  companion object {
    private const val PAYMENT_INTENT_DATA = "pi_123"
    private const val PAYMENT_INTENT_SECRET_DATA = "pisc_456"
    private const val SETUP_INTENT_DATA = "si_123"
    private const val SETUP_INTENT_SECRET_DATA = "sisc_456"

    private const val PAYMENT_RESULT = "sgnlpay://3DS?payment_intent=$PAYMENT_INTENT_DATA&payment_intent_client_secret=$PAYMENT_INTENT_SECRET_DATA"
    private const val SETUP_RESULT = "sgnlpay://3DS?setup_intent=$SETUP_INTENT_DATA&setup_intent_client_secret=$SETUP_INTENT_SECRET_DATA"
  }

  @Test
  fun `Given a URL with payment data, when I fromUri, then I expect a Secure3DSResult with matching data`() {
    val expected = StripeIntentAccessor(StripeIntentAccessor.ObjectType.PAYMENT_INTENT, PAYMENT_INTENT_DATA, PAYMENT_INTENT_SECRET_DATA)
    val result = StripeIntentAccessor.fromUri(PAYMENT_RESULT)

    assertEquals(expected, result)
  }

  @Test
  fun `Given a URL with setup data, when I fromUri, then I expect a Secure3DSResult with matching data`() {
    val expected = StripeIntentAccessor(StripeIntentAccessor.ObjectType.SETUP_INTENT, SETUP_INTENT_DATA, SETUP_INTENT_SECRET_DATA)
    val result = StripeIntentAccessor.fromUri(SETUP_RESULT)

    assertEquals(expected, result)
  }
}