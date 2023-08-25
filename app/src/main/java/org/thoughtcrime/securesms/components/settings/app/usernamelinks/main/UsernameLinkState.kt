/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

sealed class UsernameLinkState {

  /** Link is set. */
  data class Present(val link: String) : UsernameLinkState()

  /** Link has not been set yet or otherwise does not exist. */
  object NotSet : UsernameLinkState()

  /** Link is in the process of being reset. */
  object Resetting : UsernameLinkState()
}
