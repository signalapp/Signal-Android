package org.thoughtcrime.securesms.imageeditor

/**
 * Renderer that can maintain a "selected" state
 */
interface SelectableRenderer : Renderer {
  fun onSelected(selected: Boolean)
}
