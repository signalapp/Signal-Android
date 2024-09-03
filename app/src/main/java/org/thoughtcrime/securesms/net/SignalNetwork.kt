/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.signalservice.api.archive.ArchiveApi

/**
 * A convenient way to access network operations, similar to [org.thoughtcrime.securesms.database.SignalDatabase] and [org.thoughtcrime.securesms.keyvalue.SignalStore].
 */
object SignalNetwork {
  val archive: ArchiveApi
    get() = AppDependencies.archiveApi
}
