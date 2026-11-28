/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Covers each fallback the placement walks through, so the Compose renderer can be swapped in
 * underneath it without the ladder quietly changing which branch a given message lands in.
 *
 * Density is 1, so dp and px are the same number: menu padding 12, bar top padding 32, bar offset
 * 48, tight menu threshold 150.
 */
class ReactionOverlayPlacementTest {

  companion object {
    private val DENSITY = ReactionOverlayPlacement.DpConverter { it }

    private const val MENU_PADDING = 12f
    private const val BAR_TOP_PADDING = 32f

    /** A tall window, a short message near the middle, and a menu narrow enough to sit beside. */
    private val BASE = ReactionOverlayPlacement.Metrics(
      overlayWidth = 1080,
      overlayHeight = 2000,
      statusBarHeight = 60,
      navigationBarHeight = 40,
      bubbleX = 80f,
      bubbleY = 800f,
      bubbleWidth = 600,
      contextMenuX = 80f,
      snapshotWidth = 600,
      snapshotHeight = 200,
      reactionBarHeight = 120,
      scrubberForegroundHeight = 80,
      scrubberWidth = 500,
      scrubberHorizontalMargin = 24,
      menuMaxWidth = 400,
      menuMaxHeight = 500,
      isMessageOnLeft = true,
      lastSeenDownY = 900f
    )

    /** Wide enough that the menu can no longer sit beside the strip. */
    private val NARROW = BASE.copy(menuMaxWidth = 700)
  }

  private fun place(metrics: ReactionOverlayPlacement.Metrics): ReactionOverlayPlacement.Placement {
    return ReactionOverlayPlacement.of(metrics, DENSITY)
  }

  @Test
  fun `the menu goes beside the strip only when both fit across`() {
    assertThat(place(BASE).isWideLayout).isTrue()
    assertThat(place(NARROW).isWideLayout).isFalse()
  }

  @Test
  fun `a wide layout with room puts the strip just above the message`() {
    val placement = place(BASE)

    assertThat(placement.snapshotY).isEqualTo(BASE.bubbleY)
    assertThat(placement.snapshotScale).isEqualTo(1f)
    assertThat(placement.reactionBarY).isEqualTo(BASE.bubbleY - MENU_PADDING - BASE.reactionBarHeight)
  }

  @Test
  fun `a wide layout pushes the message down when the strip will not fit above it`() {
    val placement = place(BASE.copy(bubbleY = 10f))

    assertThat(placement.reactionBarY).isEqualTo(BAR_TOP_PADDING)
    assertThat(placement.snapshotY).isEqualTo(BASE.reactionBarHeight + MENU_PADDING + BAR_TOP_PADDING)
    assertThat(placement.snapshotScale).isEqualTo(1f)
  }

  @Test
  fun `a wide layout shrinks a message too tall to fit`() {
    val placement = place(BASE.copy(snapshotHeight = 1900))

    assertThat(placement.snapshotScale).isLessThan(1f)
    assertThat(placement.reactionBarY).isEqualTo(BAR_TOP_PADDING)
  }

  @Test
  fun `a narrow layout with room leaves the message where it is`() {
    val placement = place(NARROW)

    assertThat(placement.snapshotY).isEqualTo(NARROW.bubbleY)
    assertThat(placement.snapshotScale).isEqualTo(1f)
    assertThat(placement.menuHeight).isNull()
  }

  @Test
  fun `a narrow layout lifts the message when the menu will not fit below it`() {
    val placement = place(NARROW.copy(bubbleY = 1300f))

    // Room for the message and the full menu above the navigation bar, and no further.
    assertThat(placement.snapshotY).isEqualTo(1960f - NARROW.menuMaxHeight - MENU_PADDING - NARROW.snapshotHeight)
    assertThat(placement.snapshotScale).isEqualTo(1f)
  }

  @Test
  fun `a narrow layout shrinks the message when the menu takes most of the window`() {
    val placement = place(NARROW.copy(snapshotHeight = 1500))

    assertThat(placement.snapshotScale).isLessThan(1f)
    assertThat(placement.menuHeight).isNull()
  }

  @Test
  fun `the menu is halved only once nothing else fits`() {
    val placement = place(NARROW.copy(snapshotHeight = 1400, menuMaxHeight = 1800))

    assertThat(placement.menuHeight).isEqualTo(900)
  }

  @Test
  fun `the strip never rises past the status bar`() {
    val awkward = listOf(
      BASE.copy(bubbleY = -400f, snapshotHeight = 1900),
      BASE.copy(bubbleY = 0f),
      NARROW.copy(bubbleY = -900f, snapshotHeight = 1500),
      NARROW.copy(bubbleY = 1900f, snapshotHeight = 800, menuMaxHeight = 1780),
      NARROW.copy(bubbleY = -100f, snapshotHeight = 1400, menuMaxHeight = 1800),
      BASE.copy(overlayHeight = 700, snapshotHeight = 600)
    )

    for (metrics in awkward) {
      assertThat(place(metrics).reactionBarY).isGreaterThanOrEqualTo(-metrics.statusBarHeight.toFloat())
    }
  }

  @Test
  fun `the strip hugs the leading edge for an incoming message and the trailing edge for an outgoing one`() {
    assertThat(place(BASE).scrubberX).isEqualTo(BASE.scrubberHorizontalMargin.toFloat())

    val outgoing = place(BASE.copy(isMessageOnLeft = false))

    assertThat(outgoing.scrubberX).isEqualTo((BASE.overlayWidth - BASE.scrubberWidth - BASE.scrubberHorizontalMargin).toFloat())
  }

  @Test
  fun `the emoji row is centred on the strip background`() {
    val placement = place(BASE)

    val backgroundCentre = placement.reactionBarY + BASE.reactionBarHeight / 2f
    val foregroundCentre = placement.scrubberForegroundY + BASE.scrubberForegroundHeight / 2f

    assertThat(foregroundCentre).isEqualTo(backgroundCentre)
  }

  @Test
  fun `a wide menu is held on screen even when the strip is far down`() {
    val placement = place(BASE.copy(bubbleY = 1900f))

    // The strip lands at 1768, but the menu stops where its full height still fits.
    assertThat(placement.reactionBarY).isEqualTo(1768f)
    assertThat(placement.menuOffsetY).isEqualTo((1960 - BASE.menuMaxHeight).toFloat())
  }

  @Test
  fun `a wide menu sits on the far side of the strip from the message`() {
    val incoming = place(BASE)
    assertThat(incoming.menuOffsetX).isEqualTo(incoming.scrubberX + BASE.scrubberWidth + MENU_PADDING)

    val outgoing = place(BASE.copy(isMessageOnLeft = false))
    assertThat(outgoing.menuOffsetX).isEqualTo(outgoing.scrubberX - BASE.menuMaxWidth - MENU_PADDING)
  }
}
