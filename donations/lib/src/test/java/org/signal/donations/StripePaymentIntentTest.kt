package org.signal.donations

import android.app.Application
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.donations.json.StripeIntentStatus
import org.signal.donations.json.StripePaymentIntent

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class StripePaymentIntentTest {
  companion object {
    private const val TEST_JSON = """
      {
        "id": "pi_A",
        "object": "payment_intent",
        "amount": 1000,
        "amount_details": {
          "tip": {}
        },
        "automatic_payment_methods": {
          "allow_redirects": "always",
          "enabled": true
        },
        "canceled_at": null,
        "cancellation_reason": null,
        "capture_method": "automatic",
        "client_secret": "pi_client_secret",
        "confirmation_method": "automatic",
        "created": 1697568512,
        "currency": "eur",
        "description": "Thank you for supporting Signal. Your contribution helps fuel the mission of developing open source privacy technology that protects free expression and enables secure global communication for millions around the world. If you’re a resident of the United States, please retain this receipt for your tax records. Signal Technology Foundation is a tax-exempt nonprofit organization in the United States under section 501c3 of the Internal Revenue Code. Our Federal Tax ID is 82-4506840.",
        "last_payment_error": null,
        "livemode": false,
        "next_action": null,
        "payment_method": "pm_A",
        "payment_method_configuration_details": {
          "id": "pmc_A",
          "parent": null
        },
        "payment_method_types": [
          "card",
          "ideal",
          "sepa_debit"
        ],
        "processing": null,
        "receipt_email": null,
        "setup_future_usage": null,
        "shipping": null,
        "source": null,
        "status": "succeeded"
      }
    """
  }

  @Test
  fun `Given TEST_DATA, when I readValue, then I expect properly set fields`() {
    val mapper = jsonMapper {
      addModule(kotlinModule())
    }

    val intent = mapper.readValue<StripePaymentIntent>(TEST_JSON)

    assertEquals(intent.id, "pi_A")
    assertEquals(intent.clientSecret, "pi_client_secret")
    assertEquals(intent.paymentMethod, "pm_A")
    assertEquals(intent.status, StripeIntentStatus.SUCCEEDED)
  }
}
