package org.thoughtcrime.securesms.imageeditor;

import androidx.annotation.ColorInt;

/**
 * A renderer that can have its color changed.
 * <p>
 * For example, Lines and Text can change color.
 */
public interface ColorableRenderer extends Renderer {

  @ColorInt
  int getColor();

  void setColor(@ColorInt int color);
}
