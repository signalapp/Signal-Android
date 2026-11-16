/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Walks one unmerged semantics tree and reports layout problems in it.
 */
internal class LayoutInspector(private val root: SemanticsNode, rootPath: String, private val ignoreTags: Set<String>) {

  private val density: Density = root.layoutInfo.density
  private val tolerancePx: Float = with(density) { 1.dp.toPx() }
  private val nodes: List<InspectedNode> = buildList { collect(root, path = rootPath, scrollAncestor = null) }

  /**
   * Reports at most one per-node problem, picking the most likely root cause: text pushed off screen is
   * usually also squeezed out of its container, which in turn cuts it off.
   */
  fun findProblems(config: StressConfig, checks: Set<LayoutCheck>): List<LayoutProblem> {
    val problems = mutableListOf<LayoutProblem>()
    val rootBounds = root.boundsInRoot
    val offScreenIds = mutableSetOf<Int>()

    for (inspected in nodes) {
      val node = inspected.node
      val unclipped = node.unclippedBoundsInRoot

      if (node.hasAncestorIn(offScreenIds) || inspected.isClippedOutOfSight) {
        continue
      }

      if (inspected.scrollAncestor == null && (inspected.isStaticText || inspected.isClickable)) {
        val edgesCrossed = unclipped.edgesCrossed(rootBounds)
        val unreachableEdges = edgesCrossed - scrollableEdges

        if (LayoutCheck.OFF_SCREEN in checks && unreachableEdges.isNotEmpty() && unclipped.isMostlyOutside(rootBounds)) {
          offScreenIds += node.id
          val edges = unreachableEdges.joinToString(" and ") { it.name.lowercase() }
          problems += LayoutProblem(LayoutCheck.OFF_SCREEN, config, inspected.path, "Pushed off the $edges of the screen, and nothing on screen scrolls that way: ${node.describe()} at ${unclipped.describe()}, screen is ${rootBounds.describeSize()}")
          continue
        }

        // Partly scrolled away, like a collapsing top bar, so it overhangs whatever collapsed around it
        if (edgesCrossed.isNotEmpty()) {
          continue
        }
      }

      if (LayoutCheck.TEXT_OUTSIDE_CONTAINER in checks && inspected.isStaticText) {
        val container = findContainerExceeded(inspected, unclipped)
        if (container != null) {
          problems += LayoutProblem(LayoutCheck.TEXT_OUTSIDE_CONTAINER, config, inspected.path, "Text extends outside its container: ${node.describe()} is ${unclipped.describe()}, container is ${container.describe()}")
          continue
        }
      }

      if (LayoutCheck.TEXT_OVERFLOW in checks && inspected.isStaticText) {
        val layout = node.textLayoutResult
        if (layout != null && layout.isCutOff()) {
          problems += LayoutProblem(LayoutCheck.TEXT_OVERFLOW, config, inspected.path, "Text is cut off instead of wrapping or ellipsizing: ${node.describe()}")
          continue
        }
      }

      if (LayoutCheck.WORD_SPLIT in checks && inspected.isStaticText) {
        val splitWord = node.textLayoutResult?.findSplitWord()
        if (splitWord != null) {
          problems += LayoutProblem(LayoutCheck.WORD_SPLIT, config, inspected.path, "\"$splitWord\" is split across lines because its container is too narrow: ${node.describe()} is ${unclipped.describeSize()}")
        }
      }
    }

    if (LayoutCheck.OVERLAP in checks) {
      problems += findOverlaps(config)
    }

    return problems
  }

  fun textSizes(): Map<String, TextSize> {
    return nodes
      .filter { it.isStaticText }
      .mapNotNull { inspected ->
        val input = inspected.node.textLayoutResult?.layoutInput ?: return@mapNotNull null
        val fontSize = input.style.fontSize
        if (fontSize == TextUnit.Unspecified) return@mapNotNull null
        inspected.path to TextSize(with(input.density) { fontSize.toPx() }, inspected.node.describe())
      }
      .toMap()
  }

  /**
   * [path] is the node's position in the tree, e.g. "0/2/1". Unlike semantics ids, it survives the
   * content being recreated, which happens when the locale changes.
   */
  private fun MutableList<InspectedNode>.collect(node: SemanticsNode, path: String, scrollAncestor: SemanticsNode?) {
    if (node.config.getOrNull(SemanticsProperties.TestTag) in ignoreTags) {
      return
    }

    add(InspectedNode(node, path, scrollAncestor))

    val childScrollAncestor = if (node.isScrollContainer) node else scrollAncestor
    node.children.forEachIndexed { index, child -> collect(child, "$path/$index", childScrollAncestor) }
  }

  /**
   * Pairs are only compared within the same scroll container, so content scrolling under a floating
   * button isn't reported.
   */
  private fun findOverlaps(config: StressConfig): List<LayoutProblem> {
    val candidates = nodes.filter { (it.isStaticText || it.isClickable) && !it.node.boundsInRoot.isEmpty }
    val problems = mutableListOf<LayoutProblem>()

    for (i in candidates.indices) {
      for (j in i + 1 until candidates.size) {
        val a = candidates[i]
        val b = candidates[j]

        if (a.isStaticText != b.isStaticText) continue
        if (a.scrollAncestor?.id != b.scrollAncestor?.id) continue
        if (a.node.isAncestorOf(b.node) || b.node.isAncestorOf(a.node)) continue

        val overlap = a.node.boundsInRoot.intersect(b.node.boundsInRoot)
        if (overlap.width > tolerancePx && overlap.height > tolerancePx) {
          val kind = if (a.isStaticText) "Text" else "Clickable elements"
          problems += LayoutProblem(LayoutCheck.OVERLAP, config, a.path, "$kind overlap: ${a.node.describe()} and ${b.node.describe()}")
        }
      }
    }

    return problems
  }

  /**
   * Plain layout parents, like a Box with a fixed height, have no semantics node of their own, so this walks
   * the layout tree. It stops at the nearest scroll container, which is expected to hold more than it shows, and
   * at the screen, whose edges [LayoutCheck.OFF_SCREEN] covers.
   */
  private fun findContainerExceeded(inspected: InspectedNode, bounds: Rect): Rect? {
    val stopAt = inspected.scrollAncestor?.layoutInfo ?: root.layoutInfo
    var parent = inspected.node.layoutInfo.parentInfo
    while (parent != null && parent !== stopAt) {
      if (parent.isPlaced) {
        val parentBounds = Rect(parent.coordinates.positionInRoot(), parent.coordinates.size.toSize())
        if (bounds.extendsBeyond(parentBounds)) {
          return parentBounds
        }
      }
      parent = parent.parentInfo
    }
    return null
  }

  /**
   * [TextLayoutResult.hasVisualOverflow] can't be used here: the result exposed through semantics is laid out
   * against the incoming max width rather than the text's own width, so it reports width overflow for any
   * text narrower than its constraints.
   */
  private fun TextLayoutResult.isCutOff(): Boolean {
    if (layoutInput.overflow.isEllipsis) return false
    if (multiParagraph.didExceedMaxLines) return true
    if (multiParagraph.height > size.height + tolerancePx) return true
    return (0 until lineCount).any { getLineRight(it) - getLineLeft(it) > size.width + tolerancePx }
  }

  /**
   * The first word that a line break falls inside of, if any. Text that hyphenates automatically, and scripts that
   * break between any two characters, are expected to do this.
   */
  private fun TextLayoutResult.findSplitWord(): String? {
    if (layoutInput.style.hyphens == Hyphens.Auto) {
      return null
    }

    val text = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
      val breakAt = getLineEnd(line)
      if (breakAt <= 0 || breakAt >= text.length) {
        continue
      }

      val before = text.codePointBefore(breakAt)
      val after = text.codePointAt(breakAt)
      if (before.isWordCharacter() && after.isWordCharacter()) {
        val start = (breakAt - 1 downTo 0).firstOrNull { !text.codePointAt(it).isWordCharacter() }?.plus(1) ?: 0
        val end = (breakAt until text.length).firstOrNull { !text.codePointAt(it).isWordCharacter() } ?: text.length
        return text.substring(start, end)
      }
    }

    return null
  }

  private fun Rect.extendsBeyond(bounds: Rect): Boolean {
    return left < bounds.left - tolerancePx ||
      top < bounds.top - tolerancePx ||
      right > bounds.right + tolerancePx ||
      bottom > bounds.bottom + tolerancePx
  }

  /**
   * Also counts content squeezed to nothing at an edge, e.g. the last item of a column that ran out of room,
   * which doesn't extend beyond the screen because it has no size left.
   */
  private fun Rect.edgesCrossed(bounds: Rect): Set<Edge> {
    return buildSet {
      if (top < bounds.top - tolerancePx || bottom <= bounds.top + tolerancePx) add(Edge.TOP)
      if (bottom > bounds.bottom + tolerancePx || top >= bounds.bottom - tolerancePx) add(Edge.BOTTOM)
      if (left < bounds.left - tolerancePx || right <= bounds.left + tolerancePx) add(Edge.LEFT)
      if (right > bounds.right + tolerancePx || left >= bounds.right - tolerancePx) add(Edge.RIGHT)
    }
  }

  /**
   * Content that is still mostly visible is reachable, e.g. a top bar partly collapsed by nested scrolling that
   * expands again on any downward drag, which semantics don't expose. Content squeezed to nothing counts as outside.
   */
  private fun Rect.isMostlyOutside(bounds: Rect): Boolean {
    val area = width * height
    if (area <= 0f) {
      return true
    }

    val visible = intersect(bounds)
    val visibleArea = visible.width.coerceAtLeast(0f) * visible.height.coerceAtLeast(0f)
    return visibleArea < area / 2
  }

  /**
   * The edges that scrolling something on screen could bring content in from. This is screen-wide because
   * content outside a scroll container can still move with it, like a top bar that collapses through nested
   * scrolling.
   */
  private val scrollableEdges: Set<Edge> by lazy {
    buildSet {
      for (inspected in nodes) {
        val config = inspected.node.config

        config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { range ->
          val canScrollBack = range.value() > 0f
          val canScrollForward = range.value() < range.maxValue()
          if (if (range.reverseScrolling) canScrollForward else canScrollBack) add(Edge.TOP)
          if (if (range.reverseScrolling) canScrollBack else canScrollForward) add(Edge.BOTTOM)
        }

        config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { range ->
          if (range.value() > 0f || range.value() < range.maxValue()) {
            add(Edge.LEFT)
            add(Edge.RIGHT)
          }
        }
      }
    }
  }

  private fun Rect.describeSize(): String {
    return with(density) { "${width.toDp().value.toInt()}x${height.toDp().value.toInt()}dp" }
  }

  private fun Rect.describe(): String {
    return with(density) { "(${left.toDp().value.toInt()}, ${top.toDp().value.toInt()})dp ${describeSize()}" }
  }
}

private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

private val blocksWithoutWordBreaks = setOf(
  Character.UnicodeBlock.HIRAGANA,
  Character.UnicodeBlock.KATAKANA,
  Character.UnicodeBlock.THAI,
  Character.UnicodeBlock.LAO,
  Character.UnicodeBlock.KHMER,
  Character.UnicodeBlock.MYANMAR
)

private fun Int.isWordCharacter(): Boolean {
  return Character.isLetterOrDigit(this) && !Character.isIdeographic(this) && Character.UnicodeBlock.of(this) !in blocksWithoutWordBreaks
}

private class InspectedNode(val node: SemanticsNode, val path: String, val scrollAncestor: SemanticsNode?) {
  val isStaticText: Boolean = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null &&
    node.config.getOrNull(SemanticsProperties.EditableText) == null

  val isClickable: Boolean = node.config.getOrNull(SemanticsActions.OnClick) != null

  /** Hidden on purpose by a clipping ancestor, like a collapsed header, rather than cut off. */
  val isClippedOutOfSight: Boolean = node.size.width > 0 && node.size.height > 0 && node.boundsInRoot.isEmpty
}

private val TextOverflow.isEllipsis: Boolean
  get() = this == TextOverflow.Ellipsis || this == TextOverflow.StartEllipsis || this == TextOverflow.MiddleEllipsis
