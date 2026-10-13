/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews
import org.signal.registration.screens.aepentry.AepInput

class SignalLoginCredentialEntryScreenScreenshotTests {

  companion object {
    private const val ACCOUNT_ID = "a6b284822e3283d07f2391360a4c2b91"
    private const val RECOVERY_KEY = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun SignalLoginCredentialEntryScreenPreview() {
    Previews.Preview {
      SignalLoginCredentialEntryScreen(
        state = SignalLoginCredentialEntryState(
          accountId = ACCOUNT_ID,
          recoveryKey = AepInput.from(RECOVERY_KEY)
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun SignalLoginCredentialEntryScreenRevealedPreview() {
    Previews.Preview {
      SignalLoginCredentialEntryScreen(
        state = SignalLoginCredentialEntryState(
          accountId = ACCOUNT_ID,
          recoveryKey = AepInput.from(RECOVERY_KEY),
          isRecoveryKeyRevealed = true
        ),
        onEvent = {}
      )
    }
  }
}
