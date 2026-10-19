/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

fun interface EventHandler<T : Any> {
  fun onEvent(event: T)
}
