/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

/**
 * Everything that can happen on [AdvancedPrivacySettingsScreen].
 *
 * Events are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AdvancedPrivacySettingsEvent {

  /** The screen came to the foreground. */
  data object ScreenResumed : AdvancedPrivacySettingsEvent

  /** The user tapped the navigation icon. */
  data object NavigateBackClicked : AdvancedPrivacySettingsEvent

  /** The user flipped the always relay calls toggle. */
  data class AlwaysRelayCallsToggled(val enabled: Boolean) : AdvancedPrivacySettingsEvent

  /** The user flipped the censorship circumvention toggle. */
  data class CensorshipCircumventionToggled(val enabled: Boolean) : AdvancedPrivacySettingsEvent

  /** The user flipped the sealed sender status icon toggle. */
  data class SealedSenderStatusIconToggled(val enabled: Boolean) : AdvancedPrivacySettingsEvent

  /** The user flipped the allow sealed sender from anyone toggle. */
  data class SealedSenderFromAnyoneToggled(val enabled: Boolean) : AdvancedPrivacySettingsEvent

  /** The user tapped the sealed sender learn more link. */
  data object SealedSenderLearnMoreClicked : AdvancedPrivacySettingsEvent

  /** The user flipped the automatic key verification toggle. */
  data class AutomaticKeyVerificationToggled(val enabled: Boolean) : AdvancedPrivacySettingsEvent

  /** The user tapped the automatic key verification learn more link. */
  data object AutomaticKeyVerificationLearnMoreClicked : AdvancedPrivacySettingsEvent
}
