/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video.videoconverter.exceptions

/**
 * Thrown when no decoder on the device can properly decode HDR video content.
 * This is typically a device limitation, not a bug.
 */
class HdrDecoderUnavailableException(message: String, cause: Throwable?) : CodecUnavailableException(message, cause)
