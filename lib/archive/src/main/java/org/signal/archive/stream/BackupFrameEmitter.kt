/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive.stream

import org.signal.archive.proto.Frame

/**
 * An interface that lets sub-processors emit [Frame]s as they export data.
 */
fun interface BackupFrameEmitter {
  fun emit(frame: Frame)
}
