/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import org.signal.core.util.Result
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.profiles.manage.UsernameState
import org.thoughtcrime.securesms.util.NetworkUtil

/**
 * What [UsernameEditViewModel] needs from [UsernameRepository] and the stores around it.
 */
object UsernameEditRepository {

  /** The user's current username, written `nickname.discriminator`, or null if they don't have one. */
  fun getUsername(): String? = SignalStore.account.username

  fun isNetworkConnected(): Boolean = NetworkUtil.isConnected(AppDependencies.application)

  /** Stops prompting the user to create a username. */
  fun markUsernameSet() {
    SignalStore.uiHints.markHasSetOrSkippedUsernameCreation()
  }

  /** See [UsernameRepository.reserveUsername]. */
  suspend fun reserveUsername(nickname: String, discriminator: String?): Result<UsernameState.Reserved, UsernameSetResult> {
    return UsernameRepository.reserveUsername(nickname, discriminator)
  }

  /** See [UsernameRepository.updateUsernameDisplayForCurrentLink]. */
  suspend fun updateUsernameDisplayForCurrentLink(username: Username): UsernameSetResult {
    return UsernameRepository.updateUsernameDisplayForCurrentLink(username)
  }

  /** See [UsernameRepository.confirmUsernameAndCreateNewLink]. */
  suspend fun confirmUsernameAndCreateNewLink(username: Username): UsernameSetResult {
    return UsernameRepository.confirmUsernameAndCreateNewLink(username)
  }

  /** See [UsernameRepository.deleteUsernameAndLink]. */
  suspend fun deleteUsernameAndLink(): UsernameDeleteResult {
    return UsernameRepository.deleteUsernameAndLink()
  }
}
