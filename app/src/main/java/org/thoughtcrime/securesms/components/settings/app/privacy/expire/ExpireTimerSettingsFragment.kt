/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Depending on the arguments, can be used to set the universal expire timer, set expire timer
 * for a individual or group recipient, or select a value and return it via result.
 */
class ExpireTimerSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    val config = remember { arguments.toConfig() }
    ExpireTimerSettingsEntry(config = config, parentEventEmitter = appSettingsRouter::onEvent)
  }

  companion object {
    const val FOR_RESULT_VALUE = "for_result_value"
  }
}

private fun Bundle?.toConfig(): ExpireTimerSettingsViewModel.Config {
  if (this == null) {
    return ExpireTimerSettingsViewModel.Config()
  }

  val safeArguments: ExpireTimerSettingsFragmentArgs = ExpireTimerSettingsFragmentArgs.fromBundle(this)
  return ExpireTimerSettingsViewModel.Config(
    recipientId = safeArguments.recipientId,
    forResultMode = safeArguments.forResultMode,
    initialValue = safeArguments.initialValue
  )
}
