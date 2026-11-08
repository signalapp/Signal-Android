package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.hamcrest.Matchers.allOf
import org.signal.donations.InAppPaymentType
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorTestTags
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal.PayPalConfirmationDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal.PayPalConfirmationResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.Stripe3DSDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.DonationTransferTestTags
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.actions.scrollToDescendant
import org.thoughtcrime.securesms.testing.endpoints.StripeResponses
import org.thoughtcrime.securesms.testing.flushUntil

/**
 * Drives [CheckoutFlowActivity] through the UI "as a user" for a [DonationsCheckoutTestSpec]: selects
 * an amount/tier, continues to the gateway selector, and completes the chosen payment method. Each
 * payment method is a strategy dispatched on [DonationsCheckoutTestSpec.paymentMethod].
 *
 * Webview approval steps (PayPal, Stripe 3DS) can't complete against a mocked network, so instead of
 * loading the approval URL we inject the fragment result the webview would have posted — the same
 * mechanism the production dialog uses on return.
 */
class CheckoutFlowDriver(
  private val composeRule: ComposeTestRule,
  private val scheduler: TestScheduler
) {

  companion object {
    private const val TEST_NAME = "Test McTesterson"
    private const val TEST_EMAIL = "test@signal.org"

    /** A structurally valid German IBAN (passes mod-97), for the SEPA form. */
    private const val TEST_IBAN = "DE89370400440532013000"
  }

  fun drive(scenario: ActivityScenario<CheckoutFlowActivity>, spec: DonationsCheckoutTestSpec) {
    scheduler.triggerActions()

    selectAmountOrTier(spec.inAppPaymentType)
    proceedToGateway()

    when (spec.paymentMethod) {
      TestPaymentMethod.GOOGLE_PAY -> scenario.selectGooglePay(composeRule, scheduler, spec.inAppPaymentType)
      TestPaymentMethod.CREDIT_CARD -> completeCreditCard()
      TestPaymentMethod.PAYPAL -> completePayPal(scenario, spec.inAppPaymentType)
      TestPaymentMethod.SEPA_DEBIT -> completeSepa(spec.inAppPaymentType)
      TestPaymentMethod.IDEAL -> completeIdeal(spec.inAppPaymentType)
    }

    if (spec.world.stripe == StripeOutcome.REQUIRES_3DS && spec.paymentMethod.usesStripe()) {
      complete3ds(scenario, spec.inAppPaymentType)
    }
  }

  private fun selectAmountOrTier(type: InAppPaymentType) {
    if (type == InAppPaymentType.ONE_TIME_DONATION) {
      scrollToDescendant(R.id.recycler, withId(R.id.boost_1), scheduler)
      onView(allOf(withId(R.id.boost_1), isClickable())).perform(ViewActions.click())
      scheduler.triggerActions()
    }
    // Recurring: the monthly tier is selected by default, so nothing to pick here.
  }

  private fun proceedToGateway() {
    scrollToDescendant(R.id.recycler, withText(R.string.DonateToSignalFragment__continue), scheduler)
    onView(withText(R.string.DonateToSignalFragment__continue)).perform(ViewActions.click())
    scheduler.triggerActions()
  }

  /**
   * Fills the [org.thoughtcrime.securesms.components.settings.app.subscription.donate.card.CreditCardFragment]
   * with a valid test card and continues, driving the flow into the Stripe payment pipeline.
   */
  private fun completeCreditCard() {
    clickGatewayButton(GatewaySelectorTestTags.CREDIT_CARD_BUTTON)

    scheduler.flushUntil {
      onView(withId(R.id.card_number)).check(matches(isDisplayed()))
      true
    }

    onView(withId(R.id.card_number)).perform(ViewActions.typeText("4242424242424242"), ViewActions.closeSoftKeyboard())
    onView(withId(R.id.card_expiry)).perform(ViewActions.typeText("1228"), ViewActions.closeSoftKeyboard())
    onView(withId(R.id.card_cvv)).perform(ViewActions.typeText("123"), ViewActions.closeSoftKeyboard())

    scheduler.flushUntil {
      onView(withId(R.id.continue_button)).check(matches(isEnabled()))
      true
    }
    onView(withId(R.id.continue_button)).perform(ViewActions.click())
    scheduler.triggerActions()
  }

  /**
   * Selects PayPal, then injects the approval result the webview would have returned. One-time uses a
   * [PayPalConfirmationResult]; recurring posts `true`.
   */
  private fun completePayPal(scenario: ActivityScenario<CheckoutFlowActivity>, type: InAppPaymentType) {
    clickGatewayButton(GatewaySelectorTestTags.PAYPAL_BUTTON)

    val result: Any = if (type == InAppPaymentType.ONE_TIME_DONATION) {
      PayPalConfirmationResult(payerId = "test-payer", paymentId = null, paymentToken = "test-token")
    } else {
      true
    }

    awaitDialogAndSetResult(
      scenario = scenario,
      dialogClass = PayPalConfirmationDialogFragment::class.java,
      resultKey = PayPalConfirmationDialogFragment.REQUEST_KEY,
      bundle = bundleOf(PayPalConfirmationDialogFragment.REQUEST_KEY to result)
    )
  }

  /** Injects the 3DS return the Stripe webview would have posted, resuming the Stripe pipeline. */
  private fun complete3ds(scenario: ActivityScenario<CheckoutFlowActivity>, type: InAppPaymentType) {
    val accessor = if (type == InAppPaymentType.ONE_TIME_DONATION) {
      StripeIntentAccessor(StripeIntentAccessor.ObjectType.PAYMENT_INTENT, StripeResponses.DEFAULT_PAYMENT_INTENT_ID, "${StripeResponses.DEFAULT_PAYMENT_INTENT_ID}_secret_test")
    } else {
      StripeIntentAccessor(StripeIntentAccessor.ObjectType.SETUP_INTENT, StripeResponses.DEFAULT_SETUP_INTENT_ID, "${StripeResponses.DEFAULT_SETUP_INTENT_ID}_secret_test")
    }

    awaitDialogAndSetResult(
      scenario = scenario,
      dialogClass = Stripe3DSDialogFragment::class.java,
      resultKey = Stripe3DSDialogFragment.REQUEST_KEY,
      bundle = bundleOf(Stripe3DSDialogFragment.REQUEST_KEY to accessor)
    )
  }

  /**
   * Selects SEPA, accepts the mandate (its button scrolls the text to the bottom before it agrees, so
   * it is clicked until the details form appears), fills the bank details, and donates. SEPA requires
   * a EUR donation currency (see [WorldState.currencyCode]).
   */
  private fun completeSepa(type: InAppPaymentType) {
    clickGatewayButton(GatewaySelectorTestTags.SEPA_BUTTON)

    scheduler.flushUntil {
      val ibanPresent = runCatching {
        composeRule.onAllNodesWithTag(DonationTransferTestTags.SEPA_IBAN_FIELD).fetchSemanticsNodes().isNotEmpty()
      }.getOrDefault(false)
      if (!ibanPresent) {
        runCatching { composeRule.onNodeWithTag(DonationTransferTestTags.SEPA_MANDATE_CONTINUE_BUTTON).performClick() }
      }
      ibanPresent
    }

    typeIntoField(DonationTransferTestTags.SEPA_DETAILS_LIST, DonationTransferTestTags.SEPA_IBAN_FIELD, TEST_IBAN)
    typeIntoField(DonationTransferTestTags.SEPA_DETAILS_LIST, DonationTransferTestTags.SEPA_NAME_FIELD, TEST_NAME)
    typeIntoField(DonationTransferTestTags.SEPA_DETAILS_LIST, DonationTransferTestTags.SEPA_EMAIL_FIELD, TEST_EMAIL)

    clickWhenReady(DonationTransferTestTags.SEPA_DONATE_BUTTON) { paymentLeftCreatedState(type) }
  }

  /**
   * Selects iDEAL, fills the bank details (email is recurring-only), and donates. iDEAL requires a EUR
   * donation currency and, on success, lands in an awaiting-authorization state.
   */
  private fun completeIdeal(type: InAppPaymentType) {
    clickGatewayButton(GatewaySelectorTestTags.IDEAL_BUTTON)

    scheduler.flushUntil {
      runCatching {
        composeRule.onAllNodesWithTag(DonationTransferTestTags.IDEAL_NAME_FIELD).fetchSemanticsNodes().isNotEmpty()
      }.getOrDefault(false)
    }

    typeIntoField(DonationTransferTestTags.IDEAL_DETAILS_LIST, DonationTransferTestTags.IDEAL_NAME_FIELD, TEST_NAME)
    if (type.recurring) {
      typeIntoField(DonationTransferTestTags.IDEAL_DETAILS_LIST, DonationTransferTestTags.IDEAL_EMAIL_FIELD, TEST_EMAIL)
    }

    clickWhenReady(DonationTransferTestTags.IDEAL_DONATE_BUTTON) { paymentLeftCreatedState(type) }
  }

  /**
   * Scrolls the [listTag] lazy list until the field tagged [fieldTag] is composed and on-screen, then types
   * [text] into it, retrying until the field actually holds a value. The transfer forms lay their fields out
   * in a [androidx.compose.foundation.lazy.LazyColumn], so a lower field is not composed on a short screen
   * until scrolled to — without this, the input silently matches nothing on Firebase's smaller devices.
   *
   * Scroll and type have to be retried as a unit: focusing a field opens the IME, which resizes the list and
   * can recycle the *next* field back out of the composition between the scroll and the keystrokes, failing
   * the input with "the node is no longer in the tree". Re-typing is safe because [performTextInput] resolves
   * and focuses the node before inserting anything, so a throw here leaves the field untouched.
   */
  private fun typeIntoField(listTag: String, fieldTag: String, text: String) {
    scheduler.flushUntil {
      if (fieldIsFilled(fieldTag)) {
        true
      } else {
        runCatching {
          composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(fieldTag))
          composeRule.onNodeWithTag(fieldTag).performTextInput(text)
        }
        false
      }
    }
  }

  /**
   * Whether the field tagged [fieldTag] holds any text. Deliberately not an equality check against what was
   * typed: the IBAN field renders through a [androidx.compose.ui.text.input.VisualTransformation] that groups
   * the digits, so what is on screen never matches what was sent.
   */
  private fun fieldIsFilled(fieldTag: String): Boolean {
    val node = composeRule.onAllNodesWithTag(fieldTag).fetchSemanticsNodes().singleOrNull() ?: return false
    return node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotEmpty() == true
  }

  /**
   * Clicks the Compose node tagged [tag] once per flush until [settled] reports the click was acted on.
   *
   * A dispatched click is not proof the click landed, so "did it throw" is not a usable stop condition:
   * [performClick] succeeds on a disabled button, and the transfer forms pin their submit button above an
   * animating IME, so a click issued while the insets are still settling is delivered to the strip the button
   * has already vacated and silently does nothing. Retrying until [settled] holds makes the step independent
   * of where the button was a frame ago.
   *
   * [settled] has to observe the payment rather than the screen, which is why there is no UI-shaped default:
   * submit opens a view-based dialog *over* the form rather than replacing it, so the button stays composed
   * and whether Compose is even queryable depends on which window happens to be on top — it throws while the
   * dialog shows and reports matches once it goes away.
   */
  private fun clickWhenReady(tag: String, settled: () -> Boolean) {
    scheduler.flushUntil {
      if (settled()) {
        true
      } else {
        runCatching { composeRule.onNodeWithTag(tag).performClick() }
        false
      }
    }
    scheduler.triggerActions()
  }

  /**
   * Whether checkout has taken the submission for [type]: its payment row exists and has moved off the
   * CREATED state it is written in. Monotonic, unlike anything on screen — a payment never returns to
   * CREATED. Errors count as taken so a rejected submission stops the click loop and surfaces itself in the
   * spec's own assertion instead of being retried until the timeout hides it.
   */
  private fun paymentLeftCreatedState(type: InAppPaymentType): Boolean {
    val payment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(type)
    return payment != null && payment.state != InAppPaymentTable.State.CREATED
  }

  /**
   * Clicks a Compose gateway-selector button and waits for the bottom sheet to dismiss. For methods
   * other than Google Pay the checkout then navigates to the method's fragment; Google Pay instead
   * requires a result injection (see `selectGooglePay`).
   */
  private fun clickGatewayButton(tag: String) {
    scheduler.flushUntil {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    // The selector is a vertically scrolling bottom sheet; on a short screen the lower gateway buttons are
    // off-screen, so bring the target on-screen before clicking (mirrors GatewaySelectorBottomSheetContentTest).
    composeRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(tag))
    composeRule.onNodeWithTag(tag).performClick()

    scheduler.flushUntil {
      runCatching {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
      }.getOrDefault(true)
    }
  }

  /**
   * Waits for a navigated dialog of [dialogClass] to be present, then posts [bundle] as its fragment
   * result on the nav host's fragment manager (where the in-progress fragment registered its listener).
   */
  private fun <T : Fragment> awaitDialogAndSetResult(
    scenario: ActivityScenario<CheckoutFlowActivity>,
    dialogClass: Class<T>,
    resultKey: String,
    bundle: Bundle
  ) {
    scheduler.flushUntil {
      var delivered = false
      scenario.onActivity { activity ->
        val fragmentManager = navHostChildFragmentManager(activity)
        if (fragmentManager != null && fragmentManager.fragments.any { dialogClass.isInstance(it) }) {
          fragmentManager.setFragmentResult(resultKey, bundle)
          delivered = true
        }
      }
      delivered
    }
    scheduler.triggerActions()
  }

  private fun navHostChildFragmentManager(activity: FragmentActivity): FragmentManager? {
    return activity.supportFragmentManager.fragments
      .filterIsInstance<NavHostFragment>()
      .firstOrNull()
      ?.childFragmentManager
  }

  private fun TestPaymentMethod.usesStripe(): Boolean {
    return this != TestPaymentMethod.PAYPAL
  }
}
