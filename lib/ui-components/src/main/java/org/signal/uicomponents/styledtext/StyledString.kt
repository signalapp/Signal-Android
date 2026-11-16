/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.styledtext

/**
 * Plain text plus the style and link ranges to render over it with [StyledText]. Ranges index into [text].
 */
data class StyledString(
  val text: String,
  val styles: List<StyleRange> = emptyList(),
  val links: List<LinkRange> = emptyList()
) {
  override fun toString(): String = "StyledString(length=${text.length}, styles=${styles.size}, links=${links.size})"
}

/** A styled range of a [StyledString]. A [Style.SPOILER] range is hidden until the user taps it. */
data class StyleRange(val start: Int, val end: Int, val style: Style) {
  enum class Style { BOLD, ITALIC, STRIKETHROUGH, MONOSPACE, SPOILER }
}

data class LinkRange(val start: Int, val end: Int, val url: String) {
  override fun toString(): String = "LinkRange($start, $end)"
}
