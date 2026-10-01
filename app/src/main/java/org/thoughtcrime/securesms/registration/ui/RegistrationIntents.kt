/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.content.Context
import android.content.Intent
import org.signal.registration.RegistrationActivity
import org.signal.registration.RegistrationRoute
import org.thoughtcrime.securesms.MainActivity

/**
 * Builds intents for launching the registration flow.
 */
object RegistrationIntents {

  @JvmStatic
  fun newIntentForNewRegistration(context: Context): Intent {
    return RegistrationActivity.createIntent(context, nextIntent = MainActivity.clearTop(context))
  }

  @JvmStatic
  fun newIntentForReRegistration(context: Context): Intent {
    return RegistrationActivity.createIntent(context, nextIntent = MainActivity.clearTop(context), startFresh = true)
  }

  @JvmStatic
  fun newIntentForReLinkDevice(context: Context): Intent {
    return RegistrationActivity.createIntent(
      context = context,
      nextIntent = MainActivity.clearTop(context),
      startDestination = RegistrationRoute.LinkAccount(showCreateAccount = false)
    )
  }
}
