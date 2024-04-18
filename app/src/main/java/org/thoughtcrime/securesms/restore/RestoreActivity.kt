/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R

/**
 * Activity to hold the restore from backup flow.
 */
class RestoreActivity : BaseActivity() {

  private val sharedViewModel: RestoreViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_restore)
    intent.getParcelableExtraCompat(PassphraseRequiredActivity.NEXT_INTENT_EXTRA, Intent::class.java)?.let {
      sharedViewModel.setNextIntent(it)
    }
  }

  companion object {
    @JvmStatic
    fun getIntentForRestore(context: Context): Intent {
      return Intent(context, RestoreActivity::class.java)
    }
  }
}
