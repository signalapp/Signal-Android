package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.annotation.StringRes
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.endpoints.DonationResponses
import org.thoughtcrime.securesms.testing.endpoints.EndpointResponse
import org.thoughtcrime.securesms.testing.endpoints.MockEndpoints
import org.thoughtcrime.securesms.testing.endpoints.StripeResponses
import org.thoughtcrime.securesms.testing.endpoints.failure
import org.thoughtcrime.securesms.testing.endpoints.ok
import org.thoughtcrime.securesms.testing.endpoints.registerPayPalHappyPath
import org.thoughtcrime.securesms.testing.endpoints.registerStripeHappyPath
import java.util.Currency
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A declarative "state of the world" + expected outcome for one donation checkout scenario. Specs
 * are enumerated by [DonationsCheckoutSpecTest] and each one drives [CheckoutFlowActivity] end to end
 * for its [paymentMethod].
 */
data class DonationsCheckoutTestSpec(
  val name: String,
  val inAppPaymentType: InAppPaymentType,
  val paymentMethod: TestPaymentMethod,
  val world: WorldState = WorldState(),
  val expected: ExpectedOutcome
) {
  /** Used as the parameterized test label. */
  override fun toString(): String = name
}

enum class TestPaymentMethod {
  GOOGLE_PAY,
  CREDIT_CARD,
  SEPA_DEBIT,
  IDEAL,
  PAYPAL
}

/** Whether donation-permit acquisition succeeds or fails. */
enum class PermitOutcome { SUCCESS, FAILURE }

/** The Stripe HTTP edge's behavior for a scenario. */
enum class StripeOutcome {
  SUCCESS,
  DECLINE,
  FAILURE,
  REQUIRES_3DS
}

/** Pre-existing recurring subscription state (recurring scenarios only). */
enum class SubscriptionState { NONE, ACTIVE, PENDING }

/**
 * The knobs that define what each endpoint returns. [apply] registers the corresponding handlers on
 * the shared responder (overriding the [org.thoughtcrime.securesms.testing.InAppPaymentsRule]
 * defaults, since later registrations win) and sets the permit outcome.
 */
data class WorldState(
  val permit: PermitOutcome = PermitOutcome.SUCCESS,
  val stripe: StripeOutcome = StripeOutcome.SUCCESS,
  val declineCode: String = "card_declined",
  val signalCreatePaymentStatus: Int = 200,
  val receiptCredentialStatus: Int = 200,
  val redeemStatus: Int = 200,
  val existingSubscription: SubscriptionState = SubscriptionState.NONE,
  /** Donation currency to use. SEPA/iDEAL require EUR. */
  val currencyCode: String = "USD",
  /**
   * For a new recurring donation: report no active subscription until the subscription level is set
   * during setup, then report an active one. This lets the checkout show "Continue" (new subscriber)
   * while still letting the recurring receipt-credential context job see an active subscription to redeem.
   */
  val recurringActivatesAfterSetup: Boolean = false
) {
  fun apply() {
    val currency = Currency.getInstance(currencyCode)
    SignalStore.inAppPayments.setOneTimeCurrency(currency)
    SignalStore.inAppPayments.setRecurringDonationCurrency(currency)

    when (permit) {
      PermitOutcome.SUCCESS -> succeedDonationPermitAcquisition()
      PermitOutcome.FAILURE -> failDonationPermitAcquisition()
    }

    val responder = MockEndpoints.responder

    // Signal-service PayPal endpoints (harmless for non-PayPal specs; distinct paths).
    responder.registerPayPalHappyPath()

    // Stripe HTTP edge.
    responder.registerStripeHappyPath()
    when (stripe) {
      StripeOutcome.SUCCESS -> Unit
      StripeOutcome.DECLINE -> responder.register({ it.method == "POST" && (it.path.endsWith("/confirm") || it.path.contains("/payment_methods")) }) {
        failure(402, StripeResponses.declineError(declineCode))
      }
      StripeOutcome.FAILURE -> responder.register({ it.method == "POST" && it.path.endsWith("/confirm") }) {
        failure(402, StripeResponses.failureError("card_not_supported"))
      }
      StripeOutcome.REQUIRES_3DS -> responder.register({ it.method == "POST" && it.path.endsWith("/confirm") }) {
        ok(StripeResponses.confirm3dsRequired())
      }
    }

    // Signal-service edge overrides.
    if (signalCreatePaymentStatus != 200) {
      responder.register({ it.method == "POST" && (it.path.contains("/create_payment_method") || it.path.endsWith("/boost/create")) }) {
        failure(signalCreatePaymentStatus)
      }
    }
    if (receiptCredentialStatus != 200) {
      responder.register({ it.method == "POST" && it.path.contains("/receipt_credentials") }) { failure(receiptCredentialStatus) }
    }
    if (redeemStatus != 200) {
      responder.register({ it.method == "POST" && it.path.contains("/redeem-receipt") }) { failure(redeemStatus) }
    }

    when (existingSubscription) {
      SubscriptionState.NONE -> Unit
      SubscriptionState.ACTIVE -> registerSubscription(status = "active", active = true)
      SubscriptionState.PENDING -> registerSubscription(status = "incomplete", active = false)
    }

    if (recurringActivatesAfterSetup) {
      val activated = AtomicBoolean(false)
      responder.register({ it.method == "PUT" && it.path.contains("/level/") }) {
        activated.set(true)
        ok()
      }
      responder.register({ it.method == "GET" && it.path.contains("/v1/subscription/") && !it.path.contains("configuration") && !it.path.contains("bank_mandate") }) {
        if (activated.get()) ok(DonationResponses.activeSubscription()) else ok(DonationResponses.emptySubscription())
      }
    }
  }

  private fun registerSubscription(status: String, active: Boolean) {
    MockEndpoints.responder.register({ it.method == "GET" && it.path.contains("/v1/subscription/") && !it.path.contains("configuration") }) {
      EndpointResponse(status = 200, body = DonationResponses.activeSubscription(status = status, active = active))
    }
  }
}

/** The expected terminal result of driving a spec. */
sealed interface ExpectedOutcome {
  /** An error dialog with [titleRes] (and optionally [messageRes]) is shown. */
  data class ErrorDialog(@StringRes val titleRes: Int, @StringRes val messageRes: Int? = null) : ExpectedOutcome

  /** The full lifecycle completes and a donation receipt is recorded. */
  data object Redeemed : ExpectedOutcome

  /**
   * Payment setup succeeded and the payment moved past [org.thoughtcrime.securesms.database.InAppPaymentTable.State.CREATED]
   * with no error. Used for bank-transfer methods (SEPA/iDEAL), which don't redeem immediately —
   * they land in a pending / awaiting-authorization state.
   */
  data object PaymentSubmitted : ExpectedOutcome
}
