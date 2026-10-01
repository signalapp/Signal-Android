/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Central policy for whether animated (APNG) stickers should play.
 */
object StickerAnimationPolicy {

  @JvmStatic
  fun allowAnimation(): Boolean = SignalStore.settings.isAutoplayStickersAndGifsEnabled
}
