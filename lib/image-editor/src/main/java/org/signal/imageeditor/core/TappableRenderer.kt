package org.signal.imageeditor.core

/**
 * A renderer that can handle a tap event
 */
interface TappableRenderer : Renderer {
  fun onTapped()
}
