/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by whatever hosts
 * [PasskeySetupScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PasskeySetupAction {

  /** Send the user to the support article at [url]. */
  data class OpenSupportArticle(val url: String) : PasskeySetupAction

  /** Run the passkey creation ceremony, which needs an Activity to put the provider's sheet on screen. */
  data class CreatePasskey(val parameters: PasskeyCreationParameters) : PasskeySetupAction
}
