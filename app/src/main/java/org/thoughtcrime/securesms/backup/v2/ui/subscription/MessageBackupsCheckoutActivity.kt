/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity.Result

/**
 * Self-contained activity for message backups checkout, which utilizes Google Play Billing
 * instead of the normal donations routes.
 */
class MessageBackupsCheckoutActivity : FragmentWrapperActivity() {

  companion object {
    private const val RESULT_DATA = "result_data"
  }

  override fun getFragment(): Fragment = MessageBackupsFlowFragment()

  class Contract : ActivityResultContract<Unit, Result?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(context, MessageBackupsCheckoutActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
      return intent?.getParcelableExtraCompat(RESULT_DATA, Result::class.java)
    }
  }
}
