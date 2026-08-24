/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.signal.core.util.getParcelableExtraCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity.Result
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction

/**
 * Self-contained activity for message backups checkout, which utilizes Google Play Billing
 * instead of the normal donations routes.
 */
class MessageBackupsCheckoutActivity : FragmentWrapperActivity() {

  companion object {
    private const val TIER = "tier"
    private const val RESULT_DATA = "result_data"

    fun createResultData(): Intent {
      val data = bundleOf(
        RESULT_DATA to Result(
          action = InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
          inAppPaymentType = InAppPaymentType.RECURRING_BACKUP
        )
      )

      return Intent().putExtras(data)
    }
  }

  override fun getFragment(): Fragment = MessageBackupsFlowFragment.create(
    IntentCompat.getSerializableExtra(intent, TIER, MessageBackupTier::class.java)
  )

  class Contract : ActivityResultContract<MessageBackupTier?, Result?>() {

    override fun createIntent(context: Context, input: MessageBackupTier?): Intent {
      return Intent(context, MessageBackupsCheckoutActivity::class.java).putExtra(TIER, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
      return intent?.getParcelableExtraCompat(RESULT_DATA, Result::class.java)
    }
  }
}
