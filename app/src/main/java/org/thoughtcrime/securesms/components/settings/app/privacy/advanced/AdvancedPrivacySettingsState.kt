package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

data class AdvancedPrivacySettingsState(
  val isPushEnabled: Boolean,
  val alwaysRelayCalls: Boolean,
  val censorshipCircumventionState: CensorshipCircumventionState,
  val censorshipCircumventionEnabled: Boolean,
  val showSealedSenderStatusIcon: Boolean,
  val allowSealedSenderFromAnyone: Boolean,
  val showProgressSpinner: Boolean
)

enum class CensorshipCircumventionState(val available: Boolean) {
  /** The setting is unavailable because you're connected to the websocket */
  UNAVAILABLE_CONNECTED(false),

  /** The setting is unavailable because you have no network access at all */
  UNAVAILABLE_NO_INTERNET(false),

  /** The setting is available, and the user manually disabled it even though we thought they were censored */
  AVAILABLE_MANUALLY_DISABLED(true),

  /** The setting is available, and it's on because we think the user is censored */
  AVAILABLE_AUTOMATICALLY_ENABLED(true),

  /** The setting is generically available */
  AVAILABLE(true)
}
