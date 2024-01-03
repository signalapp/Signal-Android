/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.thoughtcrime.securesms.backup.v2.proto.Frame

/**
 * An interface that lets sub-processors emit [Frame]s as they export data.
 */
fun interface BackupFrameEmitter {
  fun emit(frame: Frame)
}
