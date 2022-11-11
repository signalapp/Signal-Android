package donations

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
import org.signal.donations.json.StripeSetupIntent

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class StripeSetupIntentTest {
  companion object {
    private const val TEST_JSON = """
      {
        "id": "seti_1LyzgK2eZvKYlo2C3AhgI5IC",
        "object": "setup_intent",
        "application": null,
        "cancellation_reason": null,
        "client_secret": "seti_1LyzgK2eZvKYlo2C3AhgI5IC_secret_MiQXAjP1ZBdORqQWNuJOcLqk9570HkA",
        "created": 1667229224,
        "customer": "cus_Fh6d95jDS2fVSL",
        "description": null,
        "flow_directions": null,
        "last_setup_error": null,
        "latest_attempt": null,
        "livemode": false,
        "mandate": null,
        "metadata": {},
        "next_action": null,
        "on_behalf_of": null,
        "payment_method": "pm_sldalskdjhfalskjdhf",
        "payment_method_options": {
          "card": {
            "mandate_options": null,
            "network": null,
            "request_three_d_secure": "automatic"
          }
        },
        "payment_method_types": [
          "card"
        ],
        "redaction": null,
        "single_use_mandate": null,
        "status": "requires_payment_method",
        "usage": "off_session"
      }
    """
  }

  @Test
  fun `Given TEST_DATA, when I readValue, then I expect properly set fields`() {
    val mapper = jsonMapper {
      addModule(kotlinModule())
    }

    val intent = mapper.readValue<StripeSetupIntent>(TEST_JSON)

    assertEquals(intent.id, "seti_1LyzgK2eZvKYlo2C3AhgI5IC")
    assertEquals(intent.clientSecret, "seti_1LyzgK2eZvKYlo2C3AhgI5IC_secret_MiQXAjP1ZBdORqQWNuJOcLqk9570HkA")
    assertEquals(intent.paymentMethod, "pm_sldalskdjhfalskjdhf")
    assertEquals(intent.status, StripeIntentStatus.REQUIRES_PAYMENT_METHOD)
    assertEquals(intent.customer, "cus_Fh6d95jDS2fVSL")
  }
}