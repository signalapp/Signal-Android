package org.thoughtcrime.securesms.giph.mp4;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.CornerMask;

/**
 * Describes the position and size of the area where a video should play.
 */
public final class GiphyMp4Projection {

  private final float      x;
  private final float      y;
  private final int        width;
  private final int        height;
  private final CornerMask cornerMask;

  public GiphyMp4Projection(float x, float y, int width, int height, @Nullable CornerMask cornerMask) {
    this.x          = x;
    this.y          = y;
    this.width      = width;
    this.height     = height;
    this.cornerMask = cornerMask;
  }

  public float getX() {
    return x;
  }

  public float getY() {
    return y;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public @Nullable CornerMask getCornerMask() {
    return cornerMask;
  }

  public @NonNull GiphyMp4Projection translateX(float xTranslation) {
    return new GiphyMp4Projection(x + xTranslation, y, width, height, cornerMask);
  }

  public static @NonNull GiphyMp4Projection forView(@NonNull RecyclerView recyclerView, @NonNull View view, @Nullable CornerMask cornerMask) {
    Rect viewBounds = new Rect();

    view.getDrawingRect(viewBounds);
    recyclerView.offsetDescendantRectToMyCoords(view, viewBounds);
    return new GiphyMp4Projection(viewBounds.left, viewBounds.top, view.getWidth(), view.getHeight(), cornerMask);
  }
}
