package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

data class AdvancedPrivacySettingsState(
  val isPushEnabled: Boolean,
  val alwaysRelayCalls: Boolean,
  val showSealedSenderStatusIcon: Boolean,
  val allowSealedSenderFromAnyone: Boolean,
  val showProgressSpinner: Boolean
)
