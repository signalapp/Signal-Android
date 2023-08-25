package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import org.whispersystems.signalservice.api.push.UsernameLinkComponents

/**
 * Result of resetting the username link.
 */
sealed class UsernameLinkResetResult {
  /** Successfully reset the username link. */
  data class Success(val components: UsernameLinkComponents) : UsernameLinkResetResult()

  /** Network failed when making the request. The username is still considered to be "reset".  */
  object NetworkError : UsernameLinkResetResult()

  /** We never made the request because we detected the user had no network. */
  object NetworkUnavailable : UsernameLinkResetResult()

  /** We never made the request because we hit an unexpected error. */
  object UnexpectedError : UsernameLinkResetResult()
}
