/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.colors

import org.thoughtcrime.securesms.groups.GroupId

/**
 * Stolen from iOS. Utilizes a simple hash to map different characteristics to an avatar color index.
 */
object AvatarColorHash {

  /**
   * Utilize Uppercase UUID of ServiceId.
   *
   * Uppercase is necessary here because iOS utilizes uppercase UUIDs by default.
   */
  fun forAddress(serviceId: String?, e164: String?): AvatarColor {
    if (!serviceId.isNullOrEmpty()) {
      return forSeed(serviceId.toString().uppercase())
    }

    if (!e164.isNullOrEmpty()) {
      return forSeed(e164)
    }

    return AvatarColor.A100
  }

  fun forGroupId(group: GroupId): AvatarColor {
    return forData(group.decodedId)
  }

  fun forSeed(seed: String): AvatarColor {
    return forData(seed.toByteArray())
  }

  @JvmStatic
  fun forCallLink(rootKey: ByteArray): AvatarColor {
    return forIndex(rootKey.first().toInt())
  }

  private fun forData(data: ByteArray): AvatarColor {
    var hash = 0
    for (value in data) {
      hash = hash.rotateLeft(3) xor value.toInt()
    }

    return forIndex(hash)
  }

  private fun forIndex(index: Int): AvatarColor {
    return AvatarColor.RANDOM_OPTIONS[(index.toUInt() % AvatarColor.RANDOM_OPTIONS.size.toUInt()).toInt()]
  }
}
