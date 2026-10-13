/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import org.signal.core.ui.compose.split.ListDetailEvents

/**
 * Everything the user can do in this demo.
 */
sealed interface DemoEvents {

  /** Navigating to the archive screen, which could have special processing. */
  data object ArchiveSelected : DemoEvents

  /** Something that is navigation and nothing else, so the navigator can answer it unaided. */
  data class ListDetailEvent(val event: ListDetailEvents) : DemoEvents
}
