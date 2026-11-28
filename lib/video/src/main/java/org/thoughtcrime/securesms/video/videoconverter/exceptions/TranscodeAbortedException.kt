/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video.videoconverter.exceptions

/**
 * Thrown when the transcode watchdog forcibly aborted a conversion, either because the worker
 * thread stopped making progress or because the caller requested cancellation.
 */
class TranscodeAbortedException(message: String, inner: Exception?) : EncodingException(message, inner)
