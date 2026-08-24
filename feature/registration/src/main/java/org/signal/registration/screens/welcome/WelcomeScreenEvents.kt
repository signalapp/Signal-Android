/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import org.signal.registration.RegistrationFlowState

sealed class WelcomeScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : WelcomeScreenEvents()

  data object Continue : WelcomeScreenEvents()

  data object LinkDevice : WelcomeScreenEvents()

  data object HasOldPhone : WelcomeScreenEvents()

  data object DoesNotHaveOldPhone : WelcomeScreenEvents()

  data object ViewTermsAndPrivacy : WelcomeScreenEvents()
}
