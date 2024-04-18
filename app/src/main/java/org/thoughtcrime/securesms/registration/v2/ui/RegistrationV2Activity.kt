/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationV2ViewModel

/**
 * Activity to hold the entire registration process.
 */
class RegistrationV2Activity : BaseActivity() {

  private val TAG = Log.tag(RegistrationV2Activity::class.java)

  val sharedViewModel: RegistrationV2ViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_registration_navigation_v2)
  }

  companion object {

    @JvmStatic
    fun newIntentForNewRegistration(context: Context, originalIntent: Intent): Intent {
      return Intent(context, RegistrationV2Activity::class.java).apply {
        setData(originalIntent.data)
      }
    }
  }
}
