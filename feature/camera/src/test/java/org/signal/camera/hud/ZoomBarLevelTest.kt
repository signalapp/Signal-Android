/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.signal.camera.CameraDisplay

/**
 * Covers which zoom levels are worth offering for a given window and lens, and which of them the camera counts as
 * sitting at.
 */
class ZoomBarLevelTest {

  //region What the lens can reach

  @Test
  fun `Given a lens that reaches every level, when asked what it offers, then all of them are on the bar`() {
    assertThat(ZoomBarLevel.availableIn(0.5f..10f, ROOMY))
      .containsExactly(ZoomBarLevel.HALF, ZoomBarLevel.ONE, ZoomBarLevel.TWO, ZoomBarLevel.FIVE)
  }

  /**
   * A lens that fuses an ultra-wide in reaches a hardware minimum rather than a round half — a Pixel 9 Pro Fold reports
   * 0.5058867 — and asking for the half lands there, near enough to read as the half. So the half is offered.
   */
  @Test
  fun `Given a lens that reaches just short of the half, when asked what it offers, then the half is on the bar`() {
    val available = ZoomBarLevel.availableIn(0.5058867f..20f, ROOMY)

    assertThat(available).containsExactly(ZoomBarLevel.HALF, ZoomBarLevel.ONE, ZoomBarLevel.TWO, ZoomBarLevel.FIVE)
    assertThat(ZoomBarLevel.of(zoomRatio = 0.5058867f, availableLevels = available)).isEqualTo(ZoomBarLevel.HALF)
  }

  @Test
  fun `Given a lens with no ultra wide, when asked what it offers, then the half is withheld`() {
    assertThat(ZoomBarLevel.availableIn(1f..10f, ROOMY))
      .containsExactly(ZoomBarLevel.ONE, ZoomBarLevel.TWO, ZoomBarLevel.FIVE)
  }

  /** A level the lens stops short of would be a tap that changes nothing, so it is not offered. */
  @Test
  fun `Given a lens that stops short, when asked what it offers, then the levels past it are withheld`() {
    assertThat(ZoomBarLevel.availableIn(1f..3f, ROOMY))
      .containsExactly(ZoomBarLevel.ONE, ZoomBarLevel.TWO)
  }

  @Test
  fun `Given a lens that does not quite reach the half, when asked what it offers, then it is withheld`() {
    assertThat(ZoomBarLevel.availableIn(0.6f..3f, ROOMY))
      .containsExactly(ZoomBarLevel.ONE, ZoomBarLevel.TWO)
  }

  @Test
  fun `Given a lens that does not zoom, when asked what it offers, then only the one it sits at is on the bar`() {
    assertThat(ZoomBarLevel.availableIn(1f..1f, ROOMY)).containsExactly(ZoomBarLevel.ONE)
  }

  //endregion

  //region What the window has room for

  /** The viewfinder fills the shortest window edge to edge, leaving the bar nowhere to sit above the capture button. */
  @Test
  fun `Given the shortest window, when asked what it offers, then there is room for nothing`() {
    assertThat(ZoomBarLevel.availableIn(0.5f..10f, CameraDisplay.DISPLAY_16_9)).isEmpty()
  }

  @Test
  fun `Given the next window up, when asked what it offers, then there is room for two`() {
    assertThat(ZoomBarLevel.availableIn(0.5f..10f, CameraDisplay.DISPLAY_18_9))
      .containsExactly(ZoomBarLevel.ONE, ZoomBarLevel.TWO)
  }

  @Test
  fun `Given a window with room to spare, when asked what it offers, then every level the lens reaches is on the bar`() {
    val roomy = listOf(
      CameraDisplay.DISPLAY_19_9,
      CameraDisplay.DISPLAY_20_9,
      CameraDisplay.DISPLAY_6_5,
      CameraDisplay.LARGE_PORTRAIT,
      CameraDisplay.LARGE_LANDSCAPE
    )

    roomy.forEach { cameraDisplay ->
      assertThat(ZoomBarLevel.availableIn(0.5f..10f, cameraDisplay), name = cameraDisplay.name)
        .containsExactly(ZoomBarLevel.HALF, ZoomBarLevel.ONE, ZoomBarLevel.TWO, ZoomBarLevel.FIVE)
    }
  }

  /** Room for two is a ceiling, not a promise: a lens that reaches only one still offers only that one. */
  @Test
  fun `Given the next window up and a lens that stops short, when asked what it offers, then only the reachable one is on the bar`() {
    assertThat(ZoomBarLevel.availableIn(1f..1.5f, CameraDisplay.DISPLAY_18_9))
      .containsExactly(ZoomBarLevel.ONE)
  }

  //endregion

  //region Which level the camera is sitting at

  @Test
  fun `Given the camera at a level, when asked which is showing, then it is that one`() {
    assertThat(ZoomBarLevel.of(zoomRatio = 2f, availableLevels = ALL)).isEqualTo(ZoomBarLevel.TWO)
  }

  /** A camera lands where its hardware allows rather than exactly where it was sent. */
  @Test
  fun `Given the camera just short of a level, when asked which is showing, then it is still that one`() {
    assertThat(ZoomBarLevel.of(zoomRatio = 4.95f, availableLevels = ALL)).isEqualTo(ZoomBarLevel.FIVE)
    assertThat(ZoomBarLevel.of(zoomRatio = 1.01f, availableLevels = ALL)).isEqualTo(ZoomBarLevel.ONE)
  }

  /** A ratio between two levels, which is where a pinch tends to leave the camera. */
  @Test
  fun `Given the camera between two levels, when asked which is showing, then none of them is`() {
    assertThat(ZoomBarLevel.of(zoomRatio = 3.4f, availableLevels = ALL)).isNull()
  }

  /** The tolerance is a fraction of the level, so at the long end it does not stretch to a neighbour. */
  @Test
  fun `Given the camera well short of a level, when asked which is showing, then none of them is`() {
    assertThat(ZoomBarLevel.of(zoomRatio = 4.5f, availableLevels = ALL)).isNull()
  }

  /** A ratio reached some other way cannot select a level the user was never offered. */
  @Test
  fun `Given the camera at a level the bar withheld, when asked which is showing, then none of them is`() {
    val withoutHalf = ZoomBarLevel.availableIn(1f..10f, ROOMY)

    assertThat(ZoomBarLevel.of(zoomRatio = 0.5f, availableLevels = withoutHalf)).isNull()
  }

  /** The window can withhold a level the lens reaches, and a pinch can still send the camera there. */
  @Test
  fun `Given the camera at a level the window had no room for, when asked which is showing, then none of them is`() {
    val roomForTwo = ZoomBarLevel.availableIn(0.5f..10f, CameraDisplay.DISPLAY_18_9)

    assertThat(ZoomBarLevel.of(zoomRatio = 5f, availableLevels = roomForTwo)).isNull()
  }

  //endregion

  /** The half is labeled as a ratio rather than rounded to the whole number below it. */
  @Test
  fun `Given the half, when read off the bar, then it says what it is`() {
    assertThat(ZoomBarLevel.HALF.label).isEqualTo(".5")
    assertThat(ZoomBarLevel.ONE.label).isEqualTo("1")
  }

  companion object {
    private val ALL = ZoomBarLevel.entries

    /** A window with room for every level, so a test of the lens only has to vary the lens. */
    private val ROOMY = CameraDisplay.DISPLAY_20_9
  }
}
