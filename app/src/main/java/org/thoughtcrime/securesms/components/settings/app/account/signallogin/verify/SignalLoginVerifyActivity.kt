/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin.verify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.viewModel
import org.signal.core.util.logging.Log
import org.signal.registration.screens.signallogincredentials.SignalLoginCredentialEntryScreen
import org.signal.registration.screens.signallogincredentials.SignalLoginCredentialEntryScreenActions
import org.signal.registration.screens.signallogincredentials.SignalLoginVerifyActions
import org.signal.registration.screens.signallogincredentials.SignalLoginVerifyViewModel
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.SignalLoginReminderSchedule
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Hosts [SignalLoginCredentialEntryScreen] for the Signal Login reminder megaphone, so the user can prove they still
 * know their login. Finishes with [RESULT_OK] once they do, and with [RESULT_CANCELED] otherwise.
 */
class SignalLoginVerifyActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(SignalLoginVerifyActivity::class)

    const val REQUEST_CODE = 1207

    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, SignalLoginVerifyActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val aci = SignalStore.account.aci
    val aep = SignalStore.account.accountEntropyPoolOrNull

    if (aci == null || aep == null) {
      Log.w(TAG, "There is no login on this device to verify against. aci: ${aci != null}, aep: ${aep != null}")
      finish()
      return
    }

    val viewModel by viewModel { SignalLoginVerifyViewModel(expectedAci = aci, expectedAep = aep) }

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      CollectActions(viewModel.actions) { action -> handleAction(action) }

      SignalTheme {
        SignalLoginCredentialEntryScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  private fun handleAction(action: SignalLoginVerifyActions) {
    when (action) {
      SignalLoginVerifyActions.Verified -> {
        SignalLoginReminderSchedule.onVerified()
        ViewUtil.hideKeyboard(this, window.decorView)
        setResult(RESULT_OK)
        finish()
      }
      SignalLoginVerifyActions.Close -> {
        ViewUtil.hideKeyboard(this, window.decorView)
        finish()
      }
      SignalLoginVerifyActions.OpenNeedHelpArticle -> {
        CommunicationActions.openBrowserLink(this, SignalLoginCredentialEntryScreenActions.NEED_HELP_ARTICLE_URL)
      }
    }
  }
}
