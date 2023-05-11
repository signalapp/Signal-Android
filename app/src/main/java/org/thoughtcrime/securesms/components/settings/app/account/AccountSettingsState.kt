package org.thoughtcrime.securesms.components.settings.app.account

data class AccountSettingsState(
  val hasPin: Boolean,
  val pinRemindersEnabled: Boolean,
  val registrationLockEnabled: Boolean,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean
) {
  fun isDeprecatedOrUnregistered(): Boolean {
    return !(userUnregistered || clientDeprecated)
  }
}
