package org.thoughtcrime.securesms.util;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pools;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes the position, size, and corner masking of a given view relative to a parent.
 */
public final class Projection {

  private float   x;
  private float   y;
  private int     width;
  private int     height;
  private Corners corners;
  private Path    path;
  private RectF   rect;

  private Projection() {
    x       = 0f;
    y       = 0f;
    width   = 0;
    height  = 0;
    corners = null;
    path    = new Path();
    rect    = new RectF();
  }

  private Projection set(float x, float y, int width, int height, @Nullable Corners corners) {
    this.x       = x;
    this.y       = y;
    this.width   = width;
    this.height  = height;
    this.corners = corners;

    rect.set(x, y, x + width, y + height);
    path.reset();

    if (corners != null) {
      path.addRoundRect(rect, corners.toRadii(), Path.Direction.CW);
    } else {
      path.addRect(rect, Path.Direction.CW);
    }

    return this;
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
      path.addRoundRect(rect, corners.toRadii(), Path.Direction.CW);
    }
  }

  @Override public String toString() {
    return "Projection{" +
           "x=" + x +
           ", y=" + y +
           ", width=" + width +
           ", height=" + height +
           ", corners=" + corners +
           ", path=" + path +
           ", rect=" + rect +
           '}';
  }

  public @NonNull Projection translateX(float xTranslation) {
    return set(x + xTranslation, y, width, height, corners);
  }

  public @NonNull Projection translateY(float yTranslation) {
    return set(x, y + yTranslation, width, height, corners);
  }

  public @NonNull Projection scale(float scale) {
    Corners newCorners = this.corners == null ? null : new Corners(this.corners.topLeft * scale,
                                                                   this.corners.topRight * scale,
                                                                   this.corners.bottomRight * scale,
                                                                   this.corners.bottomLeft * scale);
    return set(x, y, (int) (width * scale), (int) (height * scale), newCorners);
  }

  public @NonNull Projection insetTop(int boundary) {
    Corners newCorners = this.corners == null ? null : new Corners(0,
                                                                   0,
                                                                   this.corners.bottomRight,
                                                                   this.corners.bottomLeft);

    return set(x, y + boundary, width, height - boundary, newCorners);
  }

  public @NonNull Projection insetBottom(int boundary) {
    Corners newCorners = this.corners == null ? null : new Corners(this.corners.topLeft,
                                                                   this.corners.topRight,
                                                                   0,
                                                                   0);

    return set(x, y, width, height - boundary, newCorners);
  }

  public static @NonNull Projection relativeToParent(@NonNull ViewGroup parent, @NonNull View view, @Nullable Corners corners) {
    Rect viewBounds = new Rect();

    view.getDrawingRect(viewBounds);
    parent.offsetDescendantRectToMyCoords(view, viewBounds);
    return acquireAndSet(viewBounds.left, viewBounds.top, view.getWidth(), view.getHeight(), corners);
  }

  public static @NonNull Projection relativeToViewRoot(@NonNull View view, @Nullable Corners corners) {
    Rect      viewBounds = new Rect();
    ViewGroup root       = (ViewGroup) view.getRootView();

    view.getDrawingRect(viewBounds);
    root.offsetDescendantRectToMyCoords(view, viewBounds);

    return acquireAndSet(viewBounds.left, viewBounds.top, view.getWidth(), view.getHeight(), corners);
  }

  public static @NonNull Projection relativeToViewWithCommonRoot(@NonNull View toProject, @NonNull View viewWithCommonRoot, @Nullable Corners corners) {
    Rect      viewBounds = new Rect();
    ViewGroup root       = (ViewGroup) toProject.getRootView();

    toProject.getDrawingRect(viewBounds);
    root.offsetDescendantRectToMyCoords(toProject, viewBounds);
    root.offsetRectIntoDescendantCoords(viewWithCommonRoot, viewBounds);

    return acquireAndSet(viewBounds.left, viewBounds.top, toProject.getWidth(), toProject.getHeight(), corners);
  }

  public static @NonNull Projection translateFromDescendantToParentCoords(@NonNull Projection descendantProjection, @NonNull View descendant, @NonNull ViewGroup parent) {
    Rect viewBounds = new Rect();

    viewBounds.set((int) descendantProjection.x, (int) descendantProjection.y, (int) descendantProjection.x + descendantProjection.width, (int) descendantProjection.y + descendantProjection.height);

    parent.offsetDescendantRectToMyCoords(descendant, viewBounds);

    return acquireAndSet(viewBounds.left, viewBounds.top, descendantProjection.width, descendantProjection.height, descendantProjection.corners);
  }

  public static @NonNull List<Projection> getCapAndTail(@NonNull Projection parentProjection, @NonNull Projection childProjection) {
    if (parentProjection.equals(childProjection)) {
      return Collections.emptyList();
    }

    float topX      = parentProjection.x;
    float topY      = parentProjection.y;
    int   topWidth  = parentProjection.getWidth();
    int   topHeight = (int) (childProjection.y - parentProjection.y);

    final Corners topCorners;
    Corners       parentCorners = parentProjection.getCorners();
    if (parentCorners != null) {
      topCorners = new Corners(parentCorners.topLeft, parentCorners.topRight, 0f, 0f);
    } else {
      topCorners = null;
    }

    float bottomX      = parentProjection.x;
    float bottomY      = parentProjection.y + topHeight + childProjection.getHeight();
    int   bottomWidth  = parentProjection.getWidth();
    int   bottomHeight = (int) ((parentProjection.y + parentProjection.getHeight()) - bottomY);

    final Corners bottomCorners;
    if (parentCorners != null) {
      bottomCorners = new Corners(0f, 0f, parentCorners.bottomRight, parentCorners.bottomLeft);
    } else {
      bottomCorners = null;
    }

    return Arrays.asList(
        acquireAndSet(topX, topY, topWidth, topHeight, topCorners),
        acquireAndSet(bottomX, bottomY, bottomWidth, bottomHeight, bottomCorners)
    );
  }

  /**
   * We keep a maximum of 125 Projections around at any one time.
   */
  private static final Pools.SimplePool<Projection> projectionPool = new Pools.SimplePool<>(125);

  /**
   * Acquire a projection. This will try to grab one from the pool, and, upon failure, will
   * allocate a new one instead.
   */
  private static @NonNull Projection acquire() {
    Projection fromPool = projectionPool.acquire();
    if (fromPool != null) {
      return fromPool;
    } else {
      return new Projection();
    }
  }

  /**
   * Acquire a projection and set its fields as specified.
   */
  private static @NonNull Projection acquireAndSet(float x, float y, int width, int height, @Nullable Corners corners) {
    Projection projection = acquire();
    projection.set(x, y, width, height, corners);

    return projection;
  }

  /**
   * Projections should only be kept around for the absolute maximum amount of time they are needed.
   */
  public void release() {
    projectionPool.release(this);
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
