/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTypeTest {

  @Test
  fun `given a small window, then navigation is a bar`() {
    assertEquals(NavigationType.BAR, WindowBreakpoint.Small(isWidthExpanded = false, isHeightExpanded = false).navigationType)
  }

  /**
   * A small window is a bar however wide it reports itself: a compact height is enough to rule out a rail,
   * and that is the case a landscape phone lands in.
   */
  @Test
  fun `given a small window that is wide, then navigation is still a bar`() {
    assertEquals(NavigationType.BAR, WindowBreakpoint.Small(isWidthExpanded = true, isHeightExpanded = false).navigationType)
  }

  @Test
  fun `given a medium window that is not wide, then navigation is a bar`() {
    assertEquals(NavigationType.BAR, WindowBreakpoint.Medium(isWidthExpanded = false, isHeightExpanded = true).navigationType)
  }

  @Test
  fun `given a medium window that is wide, then navigation is a rail`() {
    assertEquals(NavigationType.RAIL, WindowBreakpoint.Medium(isWidthExpanded = true, isHeightExpanded = true).navigationType)
  }

  @Test
  fun `given a large window, then navigation is a rail`() {
    assertEquals(NavigationType.RAIL, WindowBreakpoint.Large(isWidthExpanded = true, isHeightExpanded = true).navigationType)
  }

  /**
   * A tablet in portrait is not width-expanded but is still a large window, which is what separates it from
   * the medium case above.
   */
  @Test
  fun `given a large window that is not wide, then navigation is still a rail`() {
    assertEquals(NavigationType.RAIL, WindowBreakpoint.Large(isWidthExpanded = false, isHeightExpanded = true).navigationType)
  }
}
