package org.thoughtcrime.securesms.jobs

import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Rule
import org.junit.Test
import org.signal.donations.InAppPaymentType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSetupJobData
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSourceData
import org.thoughtcrime.securesms.testing.SignalDatabaseRule

/**
 * Core test logic for [InAppPaymentSetupJob]
 */
class InAppPaymentSetupJobTest {

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun givenAnInAppPaymentThatDoesntExist_whenIRun_thenIExpectFailure() {
    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = 1L,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.CREDIT_CARD)
        .tokenData(InAppPaymentSourceData.TokenData())
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(testData)

    val result = testJob.run()

    assertThat(result.isFailure).isEqualTo(true)
  }

  @Test
  fun givenAnInAppPaymentInEndState_whenIRun_thenIExpectFailure() {
    val id = insertInAppPayment(state = InAppPaymentTable.State.END)
    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.CREDIT_CARD)
        .tokenData(InAppPaymentSourceData.TokenData())
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(testData)

    val result = testJob.run()

    assertThat(result.isFailure).isEqualTo(true)
  }

  @Test
  fun givenAnInAppPaymentInRequiredActionCompletedWithoutCompletedState_whenIRun_thenIExpectFailure() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.CREDIT_CARD)
        .tokenData(InAppPaymentSourceData.TokenData())
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(testData)

    val result = testJob.run()

    assertThat(result.isFailure).isEqualTo(true)
    assertThat(SignalDatabase.inAppPayments.getById(id)?.state).isEqualTo(InAppPaymentTable.State.END)
  }

  @Test
  fun givenAStripeInAppPaymentInRequiredActionCompletedWithCompletedState_whenIRun_thenIExpectSuccess() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED,
      data = InAppPaymentData.Builder()
        .paymentMethodType(InAppPaymentData.PaymentMethodType.CARD)
        .stripeActionComplete(InAppPaymentData.StripeActionCompleteState())
        .build()
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.CREDIT_CARD)
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(testData)

    val result = testJob.run()

    assertThat(result.isSuccess).isEqualTo(true)
  }

  @Test
  fun givenAPayPalInAppPaymentInRequiredActionCompletedWithCompletedState_whenIRun_thenIExpectSuccess() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED,
      data = InAppPaymentData.Builder()
        .paymentMethodType(InAppPaymentData.PaymentMethodType.PAYPAL)
        .payPalActionComplete(InAppPaymentData.PayPalActionCompleteState())
        .build()
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.PAY_PAL)
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(testData)

    val result = testJob.run()

    assertThat(result.isSuccess).isEqualTo(true)
  }

  @Test
  fun givenRequiredActionComplete_whenIRun_thenIBypassPerformPreUserAction() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED,
      data = InAppPaymentData.Builder()
        .paymentMethodType(InAppPaymentData.PaymentMethodType.PAYPAL)
        .payPalActionComplete(InAppPaymentData.PayPalActionCompleteState())
        .build()
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.PAY_PAL)
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(
      data = testData,
      requiredUserAction = { error("Unexpected call to requiredUserAction") },
      postUserActionResult = {
        assertThat(SignalDatabase.inAppPayments.getById(id)?.state).isEqualTo(InAppPaymentTable.State.TRANSACTING)
        Job.Result.success()
      }
    )

    val result = testJob.run()

    assertThat(result.isSuccess).isEqualTo(true)
  }

  @Test
  fun givenPayPalUserActionRequired_whenIRun_thenIDoNotPerformPostUserActionResult() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.CREATED,
      data = InAppPaymentData.Builder()
        .paymentMethodType(InAppPaymentData.PaymentMethodType.PAYPAL)
        .build()
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.PAY_PAL)
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(
      data = testData,
      requiredUserAction = {
        InAppPaymentSetupJob.RequiredUserAction.PayPalActionRequired("", "")
      },
      postUserActionResult = {
        error("Unexpected call to postUserActionResult")
      }
    )

    val result = testJob.run()

    assertThat(result.isFailure).isEqualTo(true)

    val fresh = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(fresh.state).isEqualTo(InAppPaymentTable.State.REQUIRES_ACTION)
    assertThat(fresh.data.payPalRequiresAction).isNotNull()
  }

  @Test
  fun givenStripeUserActionRequired_whenIRun_thenIDoNotPerformPostUserActionResult() {
    val id = insertInAppPayment(
      state = InAppPaymentTable.State.CREATED,
      data = InAppPaymentData.Builder()
        .paymentMethodType(InAppPaymentData.PaymentMethodType.CARD)
        .build()
    )

    val testData = InAppPaymentSetupJobData(
      inAppPaymentId = id.rowId,
      inAppPaymentSource = InAppPaymentSourceData.Builder()
        .code(InAppPaymentSourceData.Code.CREDIT_CARD)
        .build()
    )

    val testJob = TestInAppPaymentSetupJob(
      data = testData,
      requiredUserAction = {
        InAppPaymentSetupJob.RequiredUserAction.StripeActionRequired(
          StripeApi.Secure3DSAction.ConfirmRequired(
            uri = Uri.EMPTY,
            returnUri = Uri.EMPTY,
            stripeIntentAccessor = StripeIntentAccessor(
              objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
              intentId = "",
              intentClientSecret = ""
            ),
            paymentMethodId = null
          )
        )
      },
      postUserActionResult = {
        error("Unexpected call to postUserActionResult")
      }
    )

    val result = testJob.run()

    assertThat(result.isFailure).isEqualTo(true)

    val fresh = SignalDatabase.inAppPayments.getById(id)!!
    assertThat(fresh.state).isEqualTo(InAppPaymentTable.State.REQUIRES_ACTION)
    assertThat(fresh.data.stripeRequiresAction).isNotNull()
  }

  private fun insertInAppPayment(
    state: InAppPaymentTable.State = InAppPaymentTable.State.CREATED,
    data: InAppPaymentData = InAppPaymentData()
  ): InAppPaymentTable.InAppPaymentId {
    return SignalDatabase.inAppPayments.insert(
      type = InAppPaymentType.ONE_TIME_DONATION,
      state = state,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = data
    )
  }

  private class TestInAppPaymentSetupJob(
    data: InAppPaymentSetupJobData,
    val requiredUserAction: () -> RequiredUserAction = {
      RequiredUserAction.StripeActionNotRequired(
        StripeApi.Secure3DSAction.NotNeeded(
          paymentMethodId = "",
          stripeIntentAccessor = StripeIntentAccessor(
            objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
            intentId = "",
            intentClientSecret = ""
          )
        )
      )
    },
    val postUserActionResult: () -> Result = { Result.success() }
  ) : InAppPaymentSetupJob(data, Parameters.Builder().build()) {
    override fun performPreUserAction(inAppPayment: InAppPaymentTable.InAppPayment): RequiredUserAction {
      return requiredUserAction()
    }

    override fun performPostUserAction(inAppPayment: InAppPaymentTable.InAppPayment): Result {
      return postUserActionResult()
    }

    override fun getFactoryKey(): String = error("Not used.")

    override fun run(): Result {
      return performTransaction()
    }
  }
}
