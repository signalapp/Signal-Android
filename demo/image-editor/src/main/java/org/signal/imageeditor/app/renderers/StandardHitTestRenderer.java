package org.signal.imageeditor.app.renderers;

import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.Renderer;

public abstract class StandardHitTestRenderer implements Renderer {

  @Override
  public boolean hitTest(float x, float y) {
    return Bounds.contains(x, y);
  }
}
