package org.thoughtcrime.securesms.profiles.manage

import org.signal.libsignal.usernames.Username
import org.whispersystems.signalservice.api.util.discriminator
import org.whispersystems.signalservice.api.util.nickname

/**
 * Describes the state of the username suffix, which is a spanned CharSequence.
 */
sealed class UsernameState {

  protected open val username: Username? = null
  open val isInProgress: Boolean = false

  fun requireUsername(): Username = username!!

  object Loading : UsernameState() {
    override val isInProgress: Boolean = true
  }

  object NoUsername : UsernameState()

  data class Reserved(
    public override val username: Username
  ) : UsernameState()

  data class CaseChange(
    public override val username: Username
  ) : UsernameState()

  data class Set(
    override val username: Username
  ) : UsernameState()

  fun getNickname(): String? {
    return username?.nickname
  }

  fun getDiscriminator(): String? {
    return username?.discriminator
  }
}
