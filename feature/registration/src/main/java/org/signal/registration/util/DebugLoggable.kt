/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

/**
 * Interface for objects that can provide a debug-friendly string representation.
 */
interface DebugLoggable {
  fun toDebugString(): String = toString()
  fun toSafeString(): String = toString()
}
