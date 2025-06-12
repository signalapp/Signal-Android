package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.RemoteConfig
import java.math.BigDecimal
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class MessageBackupsCheckoutActivityTest {

  @get:Rule val activityRule = SignalActivityRule()

  @get:Rule val iapRule = InAppPaymentsRule()

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private val testDispatcher = StandardTestDispatcher()

  @get:Rule val coroutineDispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val purchaseResults = MutableSharedFlow<BillingPurchaseResult>()

  @Before
  fun setUp() {
    every { AppDependencies.billingApi.getBillingPurchaseResults() } returns purchaseResults
    coEvery { AppDependencies.billingApi.queryProduct() } returns BillingProduct(price = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")))
    coEvery { AppDependencies.billingApi.launchBillingFlow(any()) } returns Unit

    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.messageBackups } returns true
  }

  @Test
  fun e2e_paid_happy_path() {
    val scenario = launchCheckoutFlow()
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    e2e_shared_happy_path(context, scenario)

    composeTestRule.onNodeWithTag("message-backups-type-selection-screen-lazy-column")
      .performScrollToNode(hasText(context.getString(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)))
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)).performClick()
    composeTestRule.onNodeWithTag("subscribe-button").assertIsEnabled()
    composeTestRule.onNodeWithTag("subscribe-button").performClick()
    composeTestRule.waitForIdle()

    testDispatcher.scheduler.advanceUntilIdle()

    runBlocking {
      purchaseResults.emit(
        BillingPurchaseResult.Success(
          purchaseState = BillingPurchaseState.PURCHASED,
          purchaseToken = "asdf",
          isAcknowledged = false,
          purchaseTime = System.currentTimeMillis(),
          isAutoRenewing = true
        )
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dialog-circular-progress-indicator").assertIsDisplayed()

    testDispatcher.scheduler.advanceUntilIdle()

    val iap = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
    assertThat(iap?.state).isEqualTo(InAppPaymentTable.State.PENDING)

    SignalDatabase.inAppPayments.update(
      inAppPayment = iap!!.copy(
        state = InAppPaymentTable.State.END
      )
    )
  }

  @Test
  fun e2e_free_happy_path() {
    val scenario = launchCheckoutFlow()
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    e2e_shared_happy_path(context, scenario)

    composeTestRule.onNodeWithTag("message-backups-type-selection-screen-lazy-column")
      .performScrollToNode(hasText(context.getString(R.string.MessageBackupsTypeSelectionScreen__free)))
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsTypeSelectionScreen__free)).performClick()
    composeTestRule.onNodeWithTag("subscribe-button").assertIsEnabled()
    composeTestRule.onNodeWithTag("subscribe-button").performClick()
    composeTestRule.waitForIdle()

    assertThat(SignalStore.backup.backupTier).isEqualTo(MessageBackupTier.FREE)
  }

  private fun e2e_shared_happy_path(context: Context, scenario: ActivityScenario<MessageBackupsCheckoutActivity>) {
    assertThat(SignalStore.backup.backupTier).isNull()

    // Backup education screen
    composeTestRule.onNodeWithText(context.getString(R.string.RemoteBackupsSettingsFragment__signal_backups)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsEducationScreen__enable_backups)).performClick()

    // Key education screen
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyEducationScreen__your_backup_key)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__next)).performClick()

    // Key record screen
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__record_your_backup_key)).assertIsDisplayed()
    composeTestRule.onNodeWithTag("message-backups-key-record-screen-lazy-column")
      .performScrollToNode(hasText(context.getString(R.string.MessageBackupsKeyRecordScreen__copy_to_clipboard)))
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__copy_to_clipboard)).performClick()

    scenario.onActivity {
      val backupKeyString = SignalStore.account.accountEntropyPool.displayValue.chunked(4).joinToString("  ")
      val clipboardManager = ContextCompat.getSystemService(context, ClipboardManager::class.java)
      assertThat(clipboardManager?.primaryClip?.getItemAt(0)?.coerceToText(context)).isEqualTo(backupKeyString)
    }

    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__next)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__next)).performClick()

    // Key verification page
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyVerifyScreen__enter_the_backup_key_that_you_just_recorded)).assertIsDisplayed()
    composeTestRule.onNodeWithTag("message-backups-key-verify-screen-backup-key-input-field").performTextInput(
      SignalStore.account.accountEntropyPool.displayValue
    )
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__next)).assertIsEnabled()
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__next)).performClick()

    // Key record bottom sheet
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__keep_your_key_safe)).assertIsDisplayed()
    composeTestRule.onNodeWithTag("message-backups-key-record-screen-sheet-content")
      .performScrollToNode(hasText(context.getString(R.string.MessageBackupsKeyRecordScreen__continue)))
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__continue)).assertIsEnabled()
    composeTestRule.onNodeWithText(context.getString(R.string.MessageBackupsKeyRecordScreen__continue)).performClick()

    // Type selection screen
    composeTestRule.onNodeWithText(context.getString(R.string.MessagesBackupsTypeSelectionScreen__choose_your_backup_plan)).assertIsDisplayed()
    composeTestRule.onNodeWithTag("subscribe-button").assertIsNotEnabled()
  }

  private fun launchCheckoutFlow(tier: MessageBackupTier? = null): ActivityScenario<MessageBackupsCheckoutActivity> {
    val scenario = ActivityScenario.launch<MessageBackupsCheckoutActivity>(
      MessageBackupsCheckoutActivity.Contract().createIntent(InstrumentationRegistry.getInstrumentation().targetContext, tier)
    )

    testDispatcher.scheduler.advanceUntilIdle()
    return scenario
  }
}
