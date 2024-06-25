/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.pin

import org.thoughtcrime.securesms.util.RemoteConfig

object Svr3Migration {

  /**
   * Whether we should read from SVR3. This is a compile-time flag because it affects what happens pre-registration.
   * It only exists so that we can merge this in before SVR3 is ready server-side. This flag will be removed once we actually launch SVR3 support.
   */
  const val shouldReadFromSvr3 = false

  /**
   * Whether or not you should write to SVR3. If [shouldWriteToSvr2] is also enabled, you should write to SVR3 first.
   */
  val shouldWriteToSvr3: Boolean
    get() = shouldReadFromSvr3 && RemoteConfig.svr3MigrationPhase.let { it == 1 || it == 2 }

  /**
   * Whether or not you should write to SVR2. If [shouldWriteToSvr3] is also enabled, you should write to SVR3 first.
   */
  val shouldWriteToSvr2: Boolean
    get() = !shouldReadFromSvr3 || RemoteConfig.svr3MigrationPhase != 2
}
