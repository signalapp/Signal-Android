package org.thoughtcrime.securesms.imageeditor.renderers;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * Given points for a line to go though, automatically finds control points.
 * <p>
 * Based on  http://www.particleincell.com/2012/bezier-splines/
 * <p>
 * Can then draw that line to a {@link Canvas} given a {@link Paint}.
 * <p>
 * Allocation efficient so that adding new points does not result in lots of array allocations.
 */
final class AutomaticControlPointBezierLine implements Parcelable {

  private static final int INITIAL_CAPACITY = 256;

  private float[] x;
  private float[] y;

  // control points
  private float[] p1x;
  private float[] p1y;
  private float[] p2x;
  private float[] p2y;

  private int count;

  private final Path path = new Path();

  private AutomaticControlPointBezierLine(@Nullable float[] x, @Nullable float[] y, int count) {
    this.count = count;
    this.x = x != null ? x : new float[INITIAL_CAPACITY];
    this.y = y != null ? y : new float[INITIAL_CAPACITY];
    allocControlPointsAndWorkingMemory(this.x.length);
    recalculateControlPoints();
  }

  AutomaticControlPointBezierLine() {
    this(null, null, 0);
  }

  void reset() {
    count = 0;
    path.reset();
  }

  /**
   * Adds a new point to the end of the line but ignores points that are too close to the last.
   *
   * @param x         new x point
   * @param y         new y point
   * @param thickness the maximum distance to allow, line thickness is recommended.
   */
  void addPointFiltered(float x, float y, float thickness) {
    if (count > 0) {
      float dx = this.x[count - 1] - x;
      float dy = this.y[count - 1] - y;
      if (dx * dx + dy * dy < thickness * thickness) {
        return;
      }
    }
    addPoint(x, y);
  }

  /**
   * Adds a new point to the end of the line.
   *
   * @param x new x point
   * @param y new y point
   */
  void addPoint(float x, float y) {
    if (this.x == null || count == this.x.length) {
      resize(this.x != null ? this.x.length << 1 : INITIAL_CAPACITY);
    }

    this.x[count] = x;
    this.y[count] = y;
    count++;

    recalculateControlPoints();
  }

  private void resize(int newCapacity) {
    x = Arrays.copyOf(x, newCapacity);
    y = Arrays.copyOf(y, newCapacity);
    allocControlPointsAndWorkingMemory(newCapacity - 1);
  }

  private void allocControlPointsAndWorkingMemory(int max) {
    p1x = new float[max];
    p1y = new float[max];
    p2x = new float[max];
    p2y = new float[max];

    a = new float[max];
    b = new float[max];
    c = new float[max];
    r = new float[max];
  }

  private void recalculateControlPoints() {
    path.reset();

    if (count > 2) {
      computeControlPoints(x, p1x, p2x, count);
      computeControlPoints(y, p1y, p2y, count);
    }

    path.moveTo(x[0], y[0]);
    switch (count) {
      case 1:
        path.lineTo(x[0], y[0]);
        break;
      case 2:
        path.lineTo(x[1], y[1]);
        break;
      default:
        for (int i = 1; i < count - 1; i++) {
          path.cubicTo(p1x[i], p1y[i], p2x[i], p2y[i], x[i + 1], y[i + 1]);
        }
    }
  }

  /**
   * Draw the line.
   *
   * @param canvas The canvas to draw on.
   * @param paint  The paint to use.
   */
  void draw(@NonNull Canvas canvas, @NonNull Paint paint) {
    canvas.drawPath(path, paint);
  }

  // rhs vector for computeControlPoints method
  private float[] a;
  private float[] b;
  private float[] c;
  private float[] r;

  /**
   * Based on  http://www.particleincell.com/2012/bezier-splines/
   *
   * @param k     knots x or y, must be at least 2 entries
   * @param p1    corresponding first control point x or y
   * @param p2    corresponding second control point x or y
   * @param count number of k to process
   */
  private void computeControlPoints(float[] k, float[] p1, float[] p2, int count) {
    final int n = count - 1;

    // left most segment
    a[0] = 0;
    b[0] = 2;
    c[0] = 1;
    r[0] = k[0] + 2 * k[1];

    // internal segments
    for (int i = 1; i < n - 1; i++) {
      a[i] = 1;
      b[i] = 4;
      c[i] = 1;
      r[i] = 4 * k[i] + 2 * k[i + 1];
    }

    // right segment
    a[n - 1] = 2;
    b[n - 1] = 7;
    c[n - 1] = 0;
    r[n - 1] = 8 * k[n - 1] + k[n];

    // solves Ax=b with the Thomas algorithm
    for (int i = 1; i < n; i++) {
      float m = a[i] / b[i - 1];
      b[i] = b[i] - m * c[i - 1];
      r[i] = r[i] - m * r[i - 1];
    }

    p1[n - 1] = r[n - 1] / b[n - 1];
    for (int i = n - 2; i >= 0; --i) {
      p1[i] = (r[i] - c[i] * p1[i + 1]) / b[i];
    }

    // we have p1, now compute p2
    for (int i = 0; i < n - 1; i++) {
      p2[i] = 2 * k[i + 1] - p1[i + 1];
    }

    p2[n - 1] = 0.5f * (k[n] + p1[n - 1]);
  }

  public static final Creator<AutomaticControlPointBezierLine> CREATOR = new Creator<AutomaticControlPointBezierLine>() {
    @Override
    public AutomaticControlPointBezierLine createFromParcel(Parcel in) {
      float[] x = in.createFloatArray();
      float[] y = in.createFloatArray();
      return new AutomaticControlPointBezierLine(x, y, x != null ? x.length : 0);
    }

    @Override
    public AutomaticControlPointBezierLine[] newArray(int size) {
      return new AutomaticControlPointBezierLine[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloatArray(Arrays.copyOfRange(x, 0, count));
    dest.writeFloatArray(Arrays.copyOfRange(y, 0, count));
  }
}
