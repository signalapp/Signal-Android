/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import org.signal.camera.CameraDisplay
import kotlin.math.abs

/**
 * A zoom the bar offers as a single tap: a ratio the camera is asked to go straight to rather than one the user has to
 * drag their way to.
 *
 * @param label What the level reads as on the bar, which is the ratio rather than the name.
 */
enum class ZoomBarLevel(val zoomLevel: Float, val label: String) {
  HALF(0.5f, ".5"),
  ONE(1f, "1"),
  TWO(2f, "2"),
  FIVE(5f, "5");

  companion object {

    /**
     * How near a ratio has to be to count as being at a level, as a fraction of the level so it means the same thing at
     * every one of them. A camera lands where its hardware allows rather than exactly where it was sent — a lens that
     * fuses an ultra-wide in reaches something like 0.506 rather than a round half — and a pinch passes through a level
     * rather than settling on it.
     *
     * Both offering a level and calling it selected are measured by it, so the bar cannot withhold a level it would have
     * counted as reached, or offer one it would not.
     */
    private const val MATCH_TOLERANCE_FRACTION = 0.02f

    /**
     * The levels worth offering: the ones the viewfinder leaves room for and the lens can get near enough to. A level
     * the lens would be clamped away from is a tap that goes somewhere else.
     */
    fun availableIn(zoomRange: ClosedFloatingPointRange<Float>, cameraDisplay: CameraDisplay): List<ZoomBarLevel> = offeredBy(cameraDisplay).filter { it.isReachableIn(zoomRange) }

    /**
     * How many levels the window has room for once the viewfinder has taken its share. The bar sits above the capture
     * button on a phone and along the start side on anything larger, so the shortest window — filled by the viewfinder
     * edge to edge — has nowhere to put it, and the next one up has room for only the two levels nearest 1x.
     */
    private fun offeredBy(cameraDisplay: CameraDisplay): List<ZoomBarLevel> = when (cameraDisplay) {
      CameraDisplay.DISPLAY_16_9 -> emptyList()
      CameraDisplay.DISPLAY_18_9 -> listOf(ONE, TWO)
      else -> entries
    }

    /**
     * Which of [availableLevels] the camera is sitting at, or null for a ratio between two of them, which is where a
     * pinch or a drag along the capture button tends to leave it. Only ever a level that is on the bar, so a ratio
     * reached some other way cannot light up a level the user cannot see.
     */
    fun of(zoomRatio: Float, availableLevels: List<ZoomBarLevel>): ZoomBarLevel? = availableLevels.firstOrNull { it.isAt(zoomRatio) }

    /**
     * Whether asking for this level would land near enough to it to count. The camera clamps what it is sent into what
     * it can reach, so the clamped ratio is what the level is measured against.
     */
    private fun ZoomBarLevel.isReachableIn(zoomRange: ClosedFloatingPointRange<Float>): Boolean = isAt(zoomLevel.coerceIn(zoomRange))

    /** Whether [zoomRatio] is near enough this level to read as being at it. */
    private fun ZoomBarLevel.isAt(zoomRatio: Float): Boolean = abs(zoomRatio - zoomLevel) <= zoomLevel * MATCH_TOLERANCE_FRACTION
  }
}
