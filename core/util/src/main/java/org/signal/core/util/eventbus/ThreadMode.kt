/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

enum class ThreadMode {
  /** The handler runs synchronously on whatever thread posted the event. */
  POSTING,

  /** The handler runs on the main thread: synchronously if the event was posted there, otherwise via the main handler. */
  MAIN
}
