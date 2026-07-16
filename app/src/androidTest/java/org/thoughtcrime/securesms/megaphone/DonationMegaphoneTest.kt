/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.getSerializableCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.SignalActivityRule

/**
 * Entry-path coverage for the donations remote megaphone: the megaphone shown on the conversation list
 * that carries a "Donate" action into the one-time checkout flow.
 *
 * The megaphone renders through [MegaphoneComponent], so this renders that composable directly (rather
 * than launching the full [org.thoughtcrime.securesms.MainActivity]) and asserts that tapping Donate
 * runs the production [RemoteMegaphoneRepository] action, which navigates to [CheckoutFlowActivity]
 * with [InAppPaymentType.ONE_TIME_DONATION]. The sustainer gating is covered by [DonationMegaphoneGatingTest].
 */
@RunWith(AndroidJUnit4::class)
class DonationMegaphoneTest {

  @get:Rule
  val harness = SignalActivityRule()

  @get:Rule
  val iapRule = InAppPaymentsRule()

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    SignalDatabase.remoteMegaphones.debugRemoveAll()
  }

  @Test
  fun donateMegaphone_donateClick_opensOneTimeCheckout() {
    val record = donateMegaphoneRecord(conditionalId = null)
    SignalDatabase.remoteMegaphones.insert(record)

    val controller = RecordingMegaphoneActionController()

    composeRule.setContent {
      SignalTheme {
        MegaphoneComponent(buildDonateMegaphone(record), controller)
      }
    }

    composeRule.onNodeWithText(record.primaryActionText!!).performClick()

    val intent = controller.navigationIntent
    assertThat(intent).isNotNull()
    assertThat(intent!!.component?.className).isEqualTo(CheckoutFlowActivity::class.java.name)

    val type = intent.extras!!.getSerializableCompat(CheckoutFlowActivity.ARG_IN_APP_PAYMENT_TYPE, InAppPaymentType::class.java)
    assertThat(type).isEqualTo(InAppPaymentType.ONE_TIME_DONATION)
  }

  /** Mirrors [Megaphones.buildRemoteMegaphone]: wires the primary button to the real repository action. */
  private fun buildDonateMegaphone(record: RemoteMegaphoneRecord): Megaphone {
    return Megaphone.Builder(Megaphones.Event.REMOTE_MEGAPHONE, Megaphone.Style.BASIC)
      .setTitle(record.title)
      .setBody(record.body)
      .setActionButton(record.primaryActionText!!) { _, controller ->
        RemoteMegaphoneRepository.getAction(record.primaryActionId!!).run(context, controller, record)
      }
      .build()
  }

  private class RecordingMegaphoneActionController : MegaphoneActionController {
    var navigationIntent: Intent? = null
      private set

    override fun onMegaphoneNavigationRequested(intent: Intent) {
      navigationIntent = intent
    }

    override fun onMegaphoneNavigationRequested(intent: Intent, requestCode: Int) {
      navigationIntent = intent
    }

    override fun onMegaphoneToastRequested(string: String) = Unit
    override fun getMegaphoneActivity(): Activity = throw UnsupportedOperationException("not used")
    override fun onMegaphoneSnooze(event: Megaphones.Event) = Unit
    override fun onMegaphoneCompleted(event: Megaphones.Event) = Unit
    override fun onMegaphoneDialogFragmentRequested(dialogFragment: DialogFragment) = Unit
  }
}
