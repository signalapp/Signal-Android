/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.FileDescriptor

/**
 * A closeable handle to a real, seekable file descriptor that can be handed to platform components
 * requiring one (e.g. [android.media.MediaMuxer]) without unencrypted bytes reaching persistent
 * storage. Implementations differ only in where those bytes actually live.
 */
interface SeekableFileDescriptor : Closeable {
  val fileDescriptor: FileDescriptor
  val parcelFd: ParcelFileDescriptor
}
