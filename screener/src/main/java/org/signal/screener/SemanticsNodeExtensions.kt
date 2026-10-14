/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize

internal val SemanticsNode.isScrollContainer: Boolean
  get() = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
    config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null

internal val SemanticsNode.unclippedBoundsInRoot: Rect
  get() = Rect(positionInRoot, size.toSize())

internal val SemanticsNode.textLayoutResult: TextLayoutResult?
  get() {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
  }

internal val SemanticsNode.ancestors: Sequence<SemanticsNode>
  get() = generateSequence(parent) { it.parent }

internal fun SemanticsNode.isAncestorOf(other: SemanticsNode): Boolean = other.ancestors.any { it.id == id }

internal fun SemanticsNode.hasAncestorIn(ids: Set<Int>): Boolean = ancestors.any { it.id in ids }

internal val SemanticsNode.label: String?
  get() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
    ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

internal fun SemanticsNode.describe(): String {
  val text = firstDescendantLabel()
  val tag = config.getOrNull(SemanticsProperties.TestTag)
  return buildString {
    append("node #").append(id)
    if (tag != null) append(" tag=\"").append(tag).append('"')
    if (text != null) append(" \"").append(text.take(40)).append(if (text.length > 40) "…\"" else "\"")
  }
}

/**
 * Clickable containers usually carry no text of their own, so they are named after their first labelled descendant.
 */
internal fun SemanticsNode.firstDescendantLabel(): String? {
  return label ?: children.firstNotNullOfOrNull { it.firstDescendantLabel() }
}
