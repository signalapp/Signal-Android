/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.WindowOverlayHost
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.navigation.NavRouter
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.profiles.manage.editprofile.EditProfileNavigation
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Activity for editing your profile after you're already registered.
 */
class EditProfileActivity : PassphraseRequiredActivity() {

  companion object {
    private const val START_AT_USERNAME = "start_at_username"
    private const val START_AT_AVATAR = "start_at_avatar"

    @JvmStatic
    fun getIntent(context: Context): Intent {
      return Intent(context, EditProfileActivity::class.java)
    }

    @JvmStatic
    fun getIntentForUsernameEdit(context: Context): Intent {
      return getIntent(context).putExtra(START_AT_USERNAME, true)
    }

    @JvmStatic
    fun getIntentForAvatarEdit(context: Context): Intent {
      return getIntent(context).putExtra(START_AT_AVATAR, true)
    }
  }

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  private val router: NavRouter<AppSettingsRoute> by viewModels {
    viewModelFactory {
      initializer { NavRouter<AppSettingsRoute>(createSavedStateHandle(), AppSettingsRoute.AccountRoute.ManageProfile) }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    dynamicTheme.onCreate(this)

    if (savedInstanceState == null) {
      when {
        intent.getBooleanExtra(START_AT_USERNAME, false) -> router.navigateTo(AppSettingsRoute.AccountRoute.Username())
        intent.getBooleanExtra(START_AT_AVATAR, false) -> router.navigateTo(AppSettingsRoute.AccountRoute.AvatarPicker)
      }
    }

    setContent {
      SignalTheme {
        WindowOverlayHost {
          EditProfileNavigation(
            router = router,
            onClose = ::finish
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }
}
