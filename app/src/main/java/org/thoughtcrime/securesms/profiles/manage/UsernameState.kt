package org.thoughtcrime.securesms.profiles.manage

import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse

/**
 * Describes the state of the username suffix, which is a spanned CharSequence.
 */
sealed class UsernameState {

  protected open val username: String? = null
  open val isInProgress: Boolean = false

  object Loading : UsernameState() {
    override val isInProgress: Boolean = true
  }

  object NoUsername : UsernameState()

  data class Reserved(
    override val username: String,
    val reserveUsernameResponse: ReserveUsernameResponse
  ) : UsernameState()

  data class Set(
    override val username: String
  ) : UsernameState()

  fun getNickname(): String? {
    return username?.split(DELIMITER)?.firstOrNull()
  }

  fun getDiscriminator(): String? {
    return username?.split(DELIMITER)?.lastOrNull()
  }

  companion object {
    const val DELIMITER = "."
  }
}
