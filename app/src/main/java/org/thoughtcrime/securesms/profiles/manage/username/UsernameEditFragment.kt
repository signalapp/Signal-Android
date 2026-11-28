/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import androidx.compose.runtime.Composable
import org.signal.appsettings.routes.UsernameEditMode
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Fragment host for [UsernameEditEntry] until edit profile moves to Nav3.
 */
class UsernameEditFragment : ComposeFragment() {

  companion object {
    /** The request code to launch username recovery with, which finishes with RESULT_OK once a username is saved. */
    const val REQUEST_CODE = 4242
  }

  private val appSettingsRouter by appSettingsRouter()

  /** The edit profile graph opens this without arguments, so only the app settings graph sets a mode. */
  private val mode: UsernameEditMode
    get() = arguments?.let { UsernameEditFragmentArgs.fromBundle(it).mode } ?: UsernameEditMode.NORMAL

  @Composable
  override fun FragmentContent() {
    UsernameEditEntry(
      mode = mode,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }
}
