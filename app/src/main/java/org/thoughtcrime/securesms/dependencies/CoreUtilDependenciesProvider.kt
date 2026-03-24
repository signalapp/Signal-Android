/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.core.util.CoreUtilDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteDeprecation

object CoreUtilDependenciesProvider : CoreUtilDependencies.Provider {
  override fun provideIsClientDeprecated(): Boolean {
    return SignalStore.misc.isClientDeprecated
  }

  override fun provideTimeUntilRemoteDeprecation(currentTime: Long): Long {
    return RemoteDeprecation.getTimeUntilDeprecation(currentTime)
  }
}
