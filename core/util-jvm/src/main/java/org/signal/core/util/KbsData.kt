/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.signal.core.models.MasterKey
import org.signal.libsignal.svr2.PinHash

/**
 * Construct from a [PinHash] via [PinHashUtil].
 */
class KbsData internal constructor(
  val masterKey: MasterKey,
  val kbsAccessKey: ByteArray,
  val cipherText: ByteArray
)
