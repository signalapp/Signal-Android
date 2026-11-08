/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

import org.signal.appsettings.account.TwoFactorMethod

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out by the fragment
 * hosting [PasskeySetupScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PasskeySetupAction {

  /** Leave the screen. */
  data object NavigateBack : PasskeySetupAction

  /** Send the user to the support article at [url]. */
  data class OpenSupportArticle(val url: String) : PasskeySetupAction

  /** Run the passkey creation ceremony, which needs an Activity to put the provider's sheet on screen. */
  data class CreatePasskey(val parameters: PasskeyCreationParameters) : PasskeySetupAction

  /** Open the screen that names [method], the passkey that was just added to the account and has no name yet. */
  data class NavigateToNameNewPasskey(val method: TwoFactorMethod) : PasskeySetupAction

  /** Tell the user there's no passkey provider on the device to create a credential with. */
  data object ShowNoPasskeyProvider : PasskeySetupAction

  /** Tell the user the ceremony didn't produce a passkey. */
  data object ShowPasskeyCreationFailed : PasskeySetupAction
}
