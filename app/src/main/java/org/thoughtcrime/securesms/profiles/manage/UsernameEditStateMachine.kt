/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage

object UsernameEditStateMachine {

  enum class StateModifier {
    USER,
    SYSTEM
  }

  sealed class State {
    abstract val nickname: String
    abstract val discriminator: String
    abstract val stateModifier: StateModifier

    abstract fun onUserChangedNickname(nickname: String): State
    abstract fun onUserChangedDiscriminator(discriminator: String): State
    abstract fun onSystemChangedNickname(nickname: String): State
    abstract fun onSystemChangedDiscriminator(discriminator: String): State
  }

  /**
   * This state is representative of when the user has not manually changed either field in
   * the form, and it is assumed that both values are either blank or system provided.
   *
   * This can be thought of as our "initial state" and can be pre-populated with username information
   * for the local user.
   */
  data class NoUserEntry(
    override val nickname: String,
    override val discriminator: String,
    override val stateModifier: StateModifier
  ) : State() {
    override fun onUserChangedNickname(nickname: String): State {
      return if (nickname.isBlank()) {
        NoUserEntry(
          nickname = "",
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      } else {
        UserEnteredNickname(
          nickname = nickname,
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      }
    }

    override fun onUserChangedDiscriminator(discriminator: String): State {
      return if (discriminator.isBlank()) {
        NoUserEntry(
          nickname = nickname,
          discriminator = "",
          stateModifier = StateModifier.USER
        )
      } else {
        UserEnteredDiscriminator(
          nickname = nickname,
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      }
    }

    override fun onSystemChangedNickname(nickname: String): State {
      return copy(nickname = nickname, stateModifier = StateModifier.SYSTEM)
    }

    override fun onSystemChangedDiscriminator(discriminator: String): State {
      return copy(discriminator = discriminator, stateModifier = StateModifier.SYSTEM)
    }
  }

  /**
   * The user has altered the nickname field with something that is non-empty.
   * The user has not altered the discriminator field.
   */
  data class UserEnteredNickname(
    override val nickname: String,
    override val discriminator: String,
    override val stateModifier: StateModifier
  ) : State() {
    override fun onUserChangedNickname(nickname: String): State {
      return if (nickname.isBlank()) {
        NoUserEntry(
          nickname = "",
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      } else {
        copy(nickname = nickname, stateModifier = StateModifier.USER)
      }
    }

    override fun onUserChangedDiscriminator(discriminator: String): State {
      return if (discriminator.isBlank()) {
        copy(discriminator = "", stateModifier = StateModifier.USER)
      } else {
        UserEnteredNicknameAndDiscriminator(
          nickname = nickname,
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      }
    }

    override fun onSystemChangedNickname(nickname: String): State {
      return NoUserEntry(
        nickname = nickname,
        discriminator = discriminator,
        stateModifier = StateModifier.SYSTEM
      )
    }

    override fun onSystemChangedDiscriminator(discriminator: String): State {
      return copy(discriminator = discriminator, stateModifier = StateModifier.SYSTEM)
    }
  }

  /**
   * The user has altered the discriminator field with something that is non-empty.
   * The user has not altered the nickname field.
   */
  data class UserEnteredDiscriminator(
    override val nickname: String,
    override val discriminator: String,
    override val stateModifier: StateModifier
  ) : State() {
    override fun onUserChangedNickname(nickname: String): State {
      return if (nickname.isBlank()) {
        copy(nickname = nickname, stateModifier = StateModifier.USER)
      } else if (discriminator.isBlank()) {
        UserEnteredNickname(
          nickname = nickname,
          discriminator = "",
          stateModifier = StateModifier.USER
        )
      } else {
        UserEnteredNicknameAndDiscriminator(
          nickname = nickname,
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      }
    }

    override fun onUserChangedDiscriminator(discriminator: String): State {
      return copy(discriminator = discriminator, stateModifier = StateModifier.USER)
    }

    override fun onSystemChangedNickname(nickname: String): State {
      return copy(nickname = nickname, stateModifier = StateModifier.SYSTEM)
    }

    override fun onSystemChangedDiscriminator(discriminator: String): State {
      return NoUserEntry(
        nickname = nickname,
        discriminator = discriminator,
        stateModifier = StateModifier.SYSTEM
      )
    }
  }

  /**
   * The user has altered the nickname field with something that is non-empty.
   * The user has altered the discriminator field with something that is non-empty.
   */
  data class UserEnteredNicknameAndDiscriminator(
    override val nickname: String,
    override val discriminator: String,
    override val stateModifier: StateModifier
  ) : State() {
    override fun onUserChangedNickname(nickname: String): State {
      return if (nickname.isBlank()) {
        UserEnteredDiscriminator(
          nickname = "",
          discriminator = discriminator,
          stateModifier = StateModifier.USER
        )
      } else if (discriminator.isBlank()) {
        UserEnteredNickname(
          nickname = nickname,
          discriminator = "",
          stateModifier = StateModifier.USER
        )
      } else {
        copy(nickname = nickname, stateModifier = StateModifier.USER)
      }
    }

    override fun onUserChangedDiscriminator(discriminator: String): State {
      return copy(discriminator = discriminator, stateModifier = StateModifier.USER)
    }

    override fun onSystemChangedNickname(nickname: String): State {
      return UserEnteredDiscriminator(
        nickname = nickname,
        discriminator = discriminator,
        stateModifier = StateModifier.SYSTEM
      )
    }

    override fun onSystemChangedDiscriminator(discriminator: String): State {
      return UserEnteredNickname(
        nickname = nickname,
        discriminator = discriminator,
        stateModifier = StateModifier.SYSTEM
      )
    }
  }
}
