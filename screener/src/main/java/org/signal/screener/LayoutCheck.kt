/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

/**
 * The layout problems the screener looks for.
 */
enum class LayoutCheck {
  /** Text is cut off or drawn outside its own bounds, rather than wrapping or ellipsizing. */
  TEXT_OVERFLOW,

  /** Text extends past a container that doesn't scroll, so it is cut off or overlaps its neighbours. */
  TEXT_OUTSIDE_CONTAINER,

  /** Text or a clickable element is pushed past an edge of the screen, and nothing on screen scrolls toward that edge. */
  OFF_SCREEN,

  /** Two pieces of text, or two clickable elements, are drawn on top of each other. */
  OVERLAP,

  /** Text stays the same size when the system font scale grows, e.g. because it was sized from dp. */
  TEXT_DOES_NOT_SCALE,

  /** A line break falls inside a word, because the text's container is narrower than the word. */
  WORD_SPLIT
}

/**
 * One problem found by [check] while rendering under [config]. [nodePath] is the offending node's position in the
 * semantics tree, e.g. "0/2/1", which is stable across configs, so the same problem can be recognised in each one.
 */
data class LayoutProblem(
  val check: LayoutCheck,
  val config: StressConfig,
  val nodePath: String,
  val message: String
) {
  override fun toString(): String = "[${config.name}] $message"
}
