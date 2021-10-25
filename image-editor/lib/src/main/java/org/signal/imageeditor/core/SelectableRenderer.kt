package org.signal.imageeditor.core

import android.graphics.RectF

/**
 * Renderer that can maintain a "selected" state
 */
interface SelectableRenderer : Renderer {
  fun onSelected(selected: Boolean)

  /**
   * Get the sub bounds in local coordinates in case the selection should be shown smaller than full bounds
   */
  fun getSelectionBounds(bounds: RectF)
}
