package org.signal.imageeditor.core

/**
 * Renderer that can maintain a "selected" state
 */
interface SelectableRenderer : Renderer {
  fun onSelected(selected: Boolean)
}
