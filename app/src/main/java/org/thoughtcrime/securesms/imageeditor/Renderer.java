package org.thoughtcrime.securesms.imageeditor;

import android.os.Parcelable;
import androidx.annotation.NonNull;

/**
 * Responsible for rendering a single {@link org.thoughtcrime.securesms.imageeditor.model.EditorElement} to the canvas.
 * <p>
 * Because it knows the most about the whereabouts of the image it is also responsible for hit detection.
 */
public interface Renderer extends Parcelable {

  /**
   * Draw self to the context.
   *
   * @param rendererContext The context to draw to.
   */
  void render(@NonNull RendererContext rendererContext);

  /**
   * @param x Local coordinate X
   * @param y Local coordinate Y
   * @return true iff hit.
   */
  boolean hitTest(float x, float y);
}
