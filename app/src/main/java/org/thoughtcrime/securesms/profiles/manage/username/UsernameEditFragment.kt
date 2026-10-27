/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.appsettings.routes.UsernameEditMode
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.collectFlowOnViewLifecycle
import org.thoughtcrime.securesms.util.FragmentResultContract

/**
 * Fragment host for [UsernameEditEntry] until edit profile moves to Nav3.
 */
class UsernameEditFragment : ComposeFragment() {

  companion object {
    /** The request code to launch username recovery with, which finishes with RESULT_OK once a username is saved. */
    const val REQUEST_CODE = 4242
  }

  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  /** The edit profile graph opens this without arguments, so only the app settings graph sets a mode. */
  private val mode: UsernameEditMode
    get() = arguments?.let { UsernameEditFragmentArgs.fromBundle(it).mode } ?: UsernameEditMode.NORMAL

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    collectFlowOnViewLifecycle(appSettingsRouter.events, Lifecycle.State.RESUMED) { event ->
      when (event) {
        AppSettingsNavigationEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        is AppSettingsNavigationEvent.NavigateToScreen -> error("Unexpected route: ${event.route.javaClass.name}")
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    UsernameEditEntry(
      mode = mode,
      parentEventEmitter = appSettingsRouter::onEvent,
      onUsernameSaved = { ResultContract.setUsernameCreated(parentFragmentManager) }
    )
  }

  /** Tells the fragment beneath that a username was created. */
  class ResultContract : FragmentResultContract<Boolean>(REQUEST_KEY) {
    companion object {
      private const val REQUEST_KEY = "username_created"

      fun setUsernameCreated(fragmentManager: FragmentManager) {
        fragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to true))
      }
    }

    override fun getResult(bundle: Bundle): Boolean = bundle.getBoolean(REQUEST_KEY, false)
  }
}
