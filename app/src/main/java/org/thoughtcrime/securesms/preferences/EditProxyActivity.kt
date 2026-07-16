/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Standalone host for [EditProxyFragment], usable during registration before the user is registered.
 * Unlike [org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity], this does not extend
 * PassphraseRequiredActivity, so it won't reroute an unregistered user back into the registration flow.
 */
class EditProxyActivity : BaseActivity() {

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)

    super.onCreate(savedInstanceState)
    setContentView(R.layout.dsl_settings_activity)

    if (savedInstanceState == null) {
      val fragment = NavHostFragment.create(R.navigation.edit_proxy)
      supportFragmentManager.beginTransaction()
        .replace(R.id.nav_host_fragment, fragment)
        .commitNow()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  companion object {
    @JvmStatic
    fun intent(context: Context): Intent = Intent(context, EditProxyActivity::class.java)
  }
}
