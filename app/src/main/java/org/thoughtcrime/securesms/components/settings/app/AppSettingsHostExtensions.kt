/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SignalE164Util

/**
 * Recreates this activity when the user picks a new theme, so that app settings shows it straight away.
 */
fun AppCompatActivity.recreateOnThemeChange() {
  SignalStore.settings.onConfigurationSettingChanged.observe(this) { key ->
    if (key == SettingsValues.THEME) {
      DynamicTheme.setDefaultDayNightMode(this)
      recreate()
    }
  }
}

/**
 * Carries out the action an app settings intent asked for on launch, such as confirming a changed phone number.
 * Only on a fresh launch, so that a restored activity doesn't repeat it.
 */
fun AppCompatActivity.performActionOnCreate(savedInstanceState: Bundle?) {
  if (savedInstanceState != null) {
    return
  }

  when (intent.getStringExtra(EXTRA_PERFORM_ACTION_ON_CREATE)) {
    AppSettingsActivity.ACTION_CHANGE_NUMBER_SUCCESS -> {
      MaterialAlertDialogBuilder(this)
        .setMessage(getString(R.string.ChangeNumber__your_phone_number_has_changed_to_s, SignalE164Util.prettyPrint(SignalStore.account.requireE164())))
        .setPositiveButton(R.string.ChangeNumber__okay, null)
        .show()
    }
  }
}
