/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.util.UUID
import kotlin.random.Random

object TestRecipientUtils {

  private var upperGenAci = 13131313L
  private var lowerGenAci = 0L

  private var upperGenPni = 12121212L
  private var lowerGenPni = 0L

  private var groupMasterKeyRandom = Random(12345)

  fun generateProfileKey(): ByteArray {
    return ProfileKeyUtil.createNew().serialize()
  }

  fun nextPni(): ByteArray {
    synchronized(this) {
      lowerGenPni++
      var uuid = UUID(upperGenPni, lowerGenPni)
      return uuid.toByteArray()
    }
  }

  fun nextAci(): ByteArray {
    synchronized(this) {
      lowerGenAci++
      var uuid = UUID(upperGenAci, lowerGenAci)
      return uuid.toByteArray()
    }
  }

  fun generateGroupMasterKey(): ByteArray {
    val masterKey = ByteArray(32)
    groupMasterKeyRandom.nextBytes(masterKey)
    return masterKey
  }
}
