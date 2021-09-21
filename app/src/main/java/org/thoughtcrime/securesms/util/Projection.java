package org.thoughtcrime.securesms.util;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.CornerMask;

import java.util.Objects;

/**
 * Describes the position, size, and corner masking of a given view relative to a parent.
 */
public final class Projection {

  private final float   x;
  private final float   y;
  private final int     width;
  private final int     height;
  private final Corners corners;
  private final Path    path;
  private final RectF   rect;

  public Projection(float x, float y, int width, int height, @Nullable Corners corners) {
    this.x       = x;
    this.y       = y;
    this.width   = width;
    this.height  = height;
    this.corners = corners;
    this.path    = new Path();

    rect = new RectF();
    rect.set(x, y, x + width, y + height);

    if (corners != null) {
      path.addRoundRect(rect, corners.toRadii(), Path.Direction.CW);
    } else {
      path.addRect(rect, Path.Direction.CW);
    }
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

  public @Nullable Corners getCorners() {
    return corners;
  }

  public @NonNull Path getPath() {
    return path;
  }

  public void applyToPath(@NonNull Path path) {
    if (corners == null) {
      path.addRect(rect, Path.Direction.CW);
    } else {
      if (Build.VERSION.SDK_INT >= 21) {
        path.addRoundRect(rect, corners.toRadii(), Path.Direction.CW);
      } else {
        path.op(path, Path.Op.UNION);
      }
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Projection that = (Projection) o;
    return Float.compare(that.x, x) == 0 &&
           Float.compare(that.y, y) == 0 &&
           width == that.width &&
           height == that.height &&
           Objects.equals(corners, that.corners);
  }

  @Override public int hashCode() {
    return Objects.hash(x, y, width, height, corners);
  }

  public @NonNull Projection translateX(float xTranslation) {
    return new Projection(x + xTranslation, y, width, height, corners);
  }

  public @NonNull Projection withDimensions(int width, int height) {
    return new Projection(x, y, width, height, corners);
  }

  public @NonNull Projection withHeight(int height) {
    return new Projection(x, y, width, height, corners);
  }

  public static @NonNull Projection relativeToParent(@NonNull ViewGroup parent, @NonNull View view, @Nullable Corners corners) {
    Rect viewBounds = new Rect();

    view.getDrawingRect(viewBounds);
    parent.offsetDescendantRectToMyCoords(view, viewBounds);
    return new Projection(viewBounds.left, viewBounds.top, view.getWidth(), view.getHeight(), corners);
  }

  public static @NonNull Projection relativeToViewRoot(@NonNull View view, @Nullable Corners corners) {
    Rect      viewBounds = new Rect();
    ViewGroup root       = (ViewGroup) view.getRootView();

    view.getDrawingRect(viewBounds);
    root.offsetDescendantRectToMyCoords(view, viewBounds);

    return new Projection(viewBounds.left, viewBounds.top, view.getWidth(), view.getHeight(), corners);
  }

  public static @NonNull Projection relativeToViewWithCommonRoot(@NonNull View toProject, @NonNull View viewWithCommonRoot, @Nullable Corners corners) {
    Rect      viewBounds = new Rect();
    ViewGroup root       = (ViewGroup) toProject.getRootView();

    toProject.getDrawingRect(viewBounds);
    root.offsetDescendantRectToMyCoords(toProject, viewBounds);
    root.offsetRectIntoDescendantCoords(viewWithCommonRoot, viewBounds);

    return new Projection(viewBounds.left, viewBounds.top, toProject.getWidth(), toProject.getHeight(), corners);
  }

  public static @NonNull Projection translateFromRootToDescendantCoords(@NonNull Projection rootProjection, @NonNull View descendant) {
    Rect viewBounds = new Rect();

    viewBounds.set((int) rootProjection.x, (int) rootProjection.y, (int) rootProjection.x + rootProjection.width, (int) rootProjection.y + rootProjection.height);

    ((ViewGroup) descendant.getRootView()).offsetRectIntoDescendantCoords(descendant, viewBounds);

    return new Projection(viewBounds.left, viewBounds.top, rootProjection.width, rootProjection.height, rootProjection.corners);
  }

  public static @NonNull Projection translateFromDescendantToParentCoords(@NonNull Projection descendantProjection, @NonNull View descendant, @NonNull ViewGroup parent) {
    Rect viewBounds = new Rect();

    viewBounds.set((int) descendantProjection.x, (int) descendantProjection.y, (int) descendantProjection.x + descendantProjection.width, (int) descendantProjection.y + descendantProjection.height);

    parent.offsetDescendantRectToMyCoords(descendant, viewBounds);

    return new Projection(viewBounds.left, viewBounds.top, descendantProjection.width, descendantProjection.height, descendantProjection.corners);
  }

  public static final class Corners {
    private final float topLeft;
    private final float topRight;
    private final float bottomRight;
    private final float bottomLeft;

    public Corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
      this.topLeft     = topLeft;
      this.topRight    = topRight;
      this.bottomRight = bottomRight;
      this.bottomLeft  = bottomLeft;
    }

    public Corners(float[] radii) {
      this.topLeft     = radii[0];
      this.topRight    = radii[2];
      this.bottomRight = radii[4];
      this.bottomLeft  = radii[6];
    }

    public Corners(float radius) {
      this(radius, radius, radius, radius);
    }

    public float getTopLeft() {
      return topLeft;
    }

    public float getTopRight() {
      return topRight;
    }

    public float getBottomLeft() {
      return bottomLeft;
    }

    public float getBottomRight() {
      return bottomRight;
    }

    public float[] toRadii() {
      float[] radii = new float[8];

      radii[0] = radii[1] = topLeft;
      radii[2] = radii[3] = topRight;
      radii[4] = radii[5] = bottomRight;
      radii[6] = radii[7] = bottomLeft;

      return radii;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Corners corners = (Corners) o;
      return Float.compare(corners.topLeft, topLeft) == 0 &&
             Float.compare(corners.topRight, topRight) == 0 &&
             Float.compare(corners.bottomRight, bottomRight) == 0 &&
             Float.compare(corners.bottomLeft, bottomLeft) == 0;
    }

    @Override public int hashCode() {
      return Objects.hash(topLeft, topRight, bottomRight, bottomLeft);
    }

    @Override public String toString() {
      return "Corners{" +
             "topLeft=" + topLeft +
             ", topRight=" + topRight +
             ", bottomRight=" + bottomRight +
             ", bottomLeft=" + bottomLeft +
             '}';
    }
  }
}
