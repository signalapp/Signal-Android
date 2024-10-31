/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.whispersystems.signalservice.api.backup.MediaId
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey.MediaKeyMaterial
import org.whispersystems.signalservice.internal.util.Util

object AttachmentCipherTestHelper {

  /**
   * Needed to workaround this bug:
   * https://youtrack.jetbrains.com/issue/KT-60205/Java-class-has-private-access-in-class-constructor-with-inlinevalue-parameter
   */
  @JvmStatic
  fun createMediaKeyMaterial(combinedKey: ByteArray): MediaKeyMaterial {
    val parts = Util.split(combinedKey, 32, 32)

    return MediaKeyMaterial(
      id = MediaId(Util.getSecretBytes(15)),
      macKey = parts[1],
      aesKey = parts[0]
    )
  }
}
