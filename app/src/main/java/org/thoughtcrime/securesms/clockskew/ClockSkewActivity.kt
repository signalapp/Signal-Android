/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.viewModel

/**
 * Hosts [ClockSkewScreen], the full-screen blocking screen shown when the local device clock is too far out of sync
 * with the server's clock (see [ClockSkewDetector]). The user cannot leave until they fix their clock; the screen
 * dismisses itself once the skew is resolved (which is re-checked when the app is backgrounded/foregrounded).
 */
class ClockSkewActivity : PassphraseRequiredActivity() {

  private val theme = DynamicNoActionBarTheme()
  private val viewModel: ClockSkewViewModel by viewModel { ClockSkewViewModel() }

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    onBackPressedDispatcher.addCallback(this) {
      // Disabled: the user must fix their clock.
    }

    lifecycleScope.launch {
      viewModel.actions.collect { action ->
        when (action) {
          ClockSkewScreenAction.OpenDateSettings -> startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
          ClockSkewScreenAction.Finish -> finish()
        }
      }
    }

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      SignalTheme {
        ClockSkewScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
    viewModel.onEvent(ClockSkewScreenEvent.ScreenResumed)
  }

  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, ClockSkewActivity::class.java)
    }
  }
}
