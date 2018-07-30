/**
 * CanvasView.java
 *
 * Copyright (c) 2014 Tomohiro IKEDA (Korilakkuma)
 * Released under the MIT license
 */

package org.thoughtcrime.securesms.scribbles.widget;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This class defines fields and methods for drawing.
 */
public class CanvasView extends View {

  private static final String TAG = CanvasView.class.getSimpleName();

  public static final int DEFAULT_STROKE_WIDTH = 15;

  // Enumeration for Mode
  public enum Mode {
    DRAW,
    TEXT,
    ERASER;
  }

  // Enumeration for Drawer
  public enum Drawer {
    PEN,
    LINE,
    RECTANGLE,
    CIRCLE,
    ELLIPSE,
    QUADRATIC_BEZIER,
    QUBIC_BEZIER;
  }

  private int    canvasWidth  = 1;
  private int    canvasHeight = 1;
  private Bitmap bitmap       = null;

  private List<Path> pathLists  = new ArrayList<Path>();
  private List<Paint> paintLists = new ArrayList<Paint>();

  // for Eraser
//  private int baseColor = Color.WHITE;
  private int baseColor = Color.TRANSPARENT;

  // for Undo, Redo
  private int historyPointer = 0;

  // Flags
  private Mode mode      = Mode.DRAW;
  private Drawer drawer  = Drawer.PEN;
  private boolean isDown = false;

  // for Paint
  private Paint.Style paintStyle = Paint.Style.STROKE;
  private int paintStrokeColor   = Color.BLACK;
  private int paintFillColor     = Color.BLACK;
  private float paintStrokeWidth = DEFAULT_STROKE_WIDTH;
  private int opacity            = 255;
  private float blur             = 0F;
  private Paint.Cap lineCap      = Paint.Cap.ROUND;

  // for Drawer
  private float startX   = 0F;
  private float startY   = 0F;
  private float controlX = 0F;
  private float controlY = 0F;

  private boolean active = false;

  /**
   * Copy Constructor
   *
   * @param context
   * @param attrs
   * @param defStyle
   */
  public CanvasView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    this.setup();
  }

  /**
   * Copy Constructor
   *
   * @param context
   * @param attrs
   */
  public CanvasView(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.setup();
  }

  /**
   * Copy Constructor
   *
   * @param context
   */
  public CanvasView(Context context) {
    super(context);
  }


  private void setup() {
    this.pathLists.add(new Path());
    this.paintLists.add(this.createPaint());
    this.historyPointer++;
  }

  /**
   * This method creates the instance of Paint.
   * In addition, this method sets styles for Paint.
   *
   * @return paint This is returned as the instance of Paint
   */
  private Paint createPaint() {
    Paint paint = new Paint();

    paint.setAntiAlias(true);
    paint.setStyle(this.paintStyle);
    paint.setStrokeWidth(this.paintStrokeWidth);
    paint.setStrokeCap(this.lineCap);
    paint.setStrokeJoin(Paint.Join.ROUND);  // fixed

    if (this.mode == Mode.ERASER) {
      // Eraser
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
      paint.setARGB(0, 0, 0, 0);

      // paint.setColor(this.baseColor);
      // paint.setShadowLayer(this.blur, 0F, 0F, this.baseColor);
    } else {
      // Otherwise
      paint.setColor(this.paintStrokeColor);
      paint.setShadowLayer(this.blur, 0F, 0F, this.paintStrokeColor);
      paint.setAlpha(this.opacity);
    }

    return paint;
  }

  /**
   * This method initialize Path.
   * Namely, this method creates the instance of Path,
   * and moves current position.
   *
   * @param event This is argument of onTouchEvent method
   * @return path This is returned as the instance of Path
   */
  private Path createPath(MotionEvent event) {
    Path path = new Path();

    // Save for ACTION_MOVE
    this.startX = event.getX();
    this.startY = event.getY();

    path.moveTo(this.startX, this.startY);

    return path;
  }

  /**
   * This method updates the lists for the instance of Path and Paint.
   * "Undo" and "Redo" are enabled by this method.
   *
   * @param path the instance of Path
   */
  private void updateHistory(Path path) {
    if (this.historyPointer == this.pathLists.size()) {
      this.pathLists.add(path);
      this.paintLists.add(this.createPaint());
      this.historyPointer++;
    } else {
      // On the way of Undo or Redo
      this.pathLists.set(this.historyPointer, path);
      this.paintLists.set(this.historyPointer, this.createPaint());
      this.historyPointer++;

      for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
        this.pathLists.remove(this.historyPointer);
        this.paintLists.remove(this.historyPointer);
      }
    }
  }

  /**
   * This method gets the instance of Path that pointer indicates.
   *
   * @return the instance of Path
   */
  private Path getCurrentPath() {
    return this.pathLists.get(this.historyPointer - 1);
  }

  /**
   * This method defines processes on MotionEvent.ACTION_DOWN
   *
   * @param event This is argument of onTouchEvent method
   */
  private void onActionDown(MotionEvent event) {
    switch (this.mode) {
      case DRAW   :
      case ERASER :
        if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {
          // Oherwise
          this.updateHistory(this.createPath(event));
          this.isDown = true;
        } else {
          // Bezier
          if ((this.startX == 0F) && (this.startY == 0F)) {
            // The 1st tap
            this.updateHistory(this.createPath(event));
          } else {
            // The 2nd tap
            this.controlX = event.getX();
            this.controlY = event.getY();

            this.isDown = true;
          }
        }

        break;
      case TEXT   :
        this.startX = event.getX();
        this.startY = event.getY();

        break;
      default :
        break;
    }
  }

  /**
   * This method defines processes on MotionEvent.ACTION_MOVE
   *
   * @param event This is argument of onTouchEvent method
   */
  private void onActionMove(MotionEvent event) {
    float x = event.getX();
    float y = event.getY();

    switch (this.mode) {
      case DRAW   :
      case ERASER :

        if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {
          if (!isDown) {
            return;
          }

          Path path = this.getCurrentPath();

          switch (this.drawer) {
            case PEN :
              for (int i = 0; i < event.getHistorySize(); i++) {
                path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i));
              }
              break;
            case LINE :
              path.reset();
              path.moveTo(this.startX, this.startY);
              path.lineTo(x, y);
              break;
            case RECTANGLE :
              path.reset();
              path.addRect(this.startX, this.startY, x, y, Path.Direction.CCW);
              break;
            case CIRCLE :
              double distanceX = Math.abs((double)(this.startX - x));
              double distanceY = Math.abs((double)(this.startX - y));
              double radius    = Math.sqrt(Math.pow(distanceX, 2.0) + Math.pow(distanceY, 2.0));

              path.reset();
              path.addCircle(this.startX, this.startY, (float)radius, Path.Direction.CCW);
              break;
            case ELLIPSE :
              RectF rect = new RectF(this.startX, this.startY, x, y);

              path.reset();
              path.addOval(rect, Path.Direction.CCW);
              break;
            default :
              break;
          }
        } else {
          if (!isDown) {
            return;
          }

          Path path = this.getCurrentPath();

          path.reset();
          path.moveTo(this.startX, this.startY);
          path.quadTo(this.controlX, this.controlY, x, y);
        }

        break;
      case TEXT :
        this.startX = x;
        this.startY = y;

        break;
      default :
        break;
    }
  }

  /**
   * This method defines processes on MotionEvent.ACTION_DOWN
   *
   * @param event This is argument of onTouchEvent method
   */
  private void onActionUp(MotionEvent event) {
    if (isDown) {
      this.startX = 0F;
      this.startY = 0F;
      this.isDown = false;
    }
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  /**
   * This method updates the instance of Canvas (View)
   *
   * @param canvas the new instance of Canvas
   */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // Before "drawPath"
    canvas.drawColor(this.baseColor);

    if (this.bitmap != null) {
      canvas.drawBitmap(this.bitmap, 0F, 0F, new Paint());
    }

    for (int i = 0; i < this.historyPointer; i++) {
      Path path   = this.pathLists.get(i);
      Paint paint = this.paintLists.get(i);

      canvas.drawPath(path, paint);
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    this.canvasWidth = w;
    this.canvasHeight = h;
  }

  public void render(Canvas canvas) {
    float scaleX = 1.0F * canvas.getWidth() / canvasWidth;
    float scaleY = 1.0F * canvas.getHeight() / canvasHeight;

    Matrix matrix = new Matrix();
    matrix.setScale(scaleX, scaleY);

    for (int i = 0; i < this.historyPointer; i++) {
      Path path   = this.pathLists.get(i);
      Paint paint = this.paintLists.get(i);

      Path scaledPath = new Path();
      path.transform(matrix, scaledPath);

      Paint scaledPaint = new Paint(paint);
      scaledPaint.setStrokeWidth(scaledPaint.getStrokeWidth() * scaleX);

      canvas.drawPath(scaledPath, scaledPaint);
    }
  }

  /**
   * This method set event listener for drawing.
   *
   * @param event the instance of MotionEvent
   * @return
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!active) return false;

    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        this.onActionDown(event);
        break;
      case MotionEvent.ACTION_MOVE :
        this.onActionMove(event);
        break;
      case MotionEvent.ACTION_UP :
        this.onActionUp(event);
        break;
      default :
        break;
    }

    // Re draw
    this.invalidate();

    return true;
  }

  /**
   * This method is getter for mode.
   *
   * @return
   */
  public Mode getMode() {
    return this.mode;
  }

  /**
   * This method is setter for mode.
   *
   * @param mode
   */
  public void setMode(Mode mode) {
    this.mode = mode;
  }

  /**
   * This method is getter for drawer.
   *
   * @return
   */
  public Drawer getDrawer() {
    return this.drawer;
  }

  /**
   * This method is setter for drawer.
   *
   * @param drawer
   */
  public void setDrawer(Drawer drawer) {
    this.drawer = drawer;
  }

  /**
   * This method draws canvas again for Undo.
   *
   * @return If Undo is enabled, this is returned as true. Otherwise, this is returned as false.
   */
  public boolean undo() {
    if (this.historyPointer > 1) {
      this.historyPointer--;
      this.invalidate();

      return true;
    } else {
      return false;
    }
  }

  /**
   * This method draws canvas again for Redo.
   *
   * @return If Redo is enabled, this is returned as true. Otherwise, this is returned as false.
   */
  public boolean redo() {
    if (this.historyPointer < this.pathLists.size()) {
      this.historyPointer++;
      this.invalidate();

      return true;
    } else {
      return false;
    }
  }

  /**
   * This method initializes canvas.
   *
   * @return
   */
  public void clear() {
    Path path = new Path();
    path.moveTo(0F, 0F);
    path.addRect(0F, 0F, 1000F, 1000F, Path.Direction.CCW);
    path.close();

    Paint paint = new Paint();
    paint.setColor(Color.WHITE);
    paint.setStyle(Paint.Style.FILL);

    if (this.historyPointer == this.pathLists.size()) {
      this.pathLists.add(path);
      this.paintLists.add(paint);
      this.historyPointer++;
    } else {
      // On the way of Undo or Redo
      this.pathLists.set(this.historyPointer, path);
      this.paintLists.set(this.historyPointer, paint);
      this.historyPointer++;

      for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
        this.pathLists.remove(this.historyPointer);
        this.paintLists.remove(this.historyPointer);
      }
    }

    // Clear
    this.invalidate();
  }

  /**
   * This method is getter for canvas background color
   *
   * @return
   */
  public int getBaseColor() {
    return this.baseColor;
  }

  /**
   * This method is setter for canvas background color
   *
   * @param color
   */
  public void setBaseColor(int color) {
    this.baseColor = color;
  }

  /**
   * This method is getter for stroke or fill.
   *
   * @return
   */
  public Paint.Style getPaintStyle() {
    return this.paintStyle;
  }

  /**
   * This method is setter for stroke or fill.
   *
   * @param style
   */
  public void setPaintStyle(Paint.Style style) {
    this.paintStyle = style;
  }

  /**
   * This method is getter for stroke color.
   *
   * @return
   */
  public int getPaintStrokeColor() {
    return this.paintStrokeColor;
  }

  /**
   * This method is setter for stroke color.
   *
   * @param color
   */
  public void setPaintStrokeColor(int color) {
    this.paintStrokeColor = color;
  }

  /**
   * This method is getter for fill color.
   * But, current Android API cannot set fill color (?).
   *
   * @return
   */
  public int getPaintFillColor() {
    return this.paintFillColor;
  };

  /**
   * This method is setter for fill color.
   * But, current Android API cannot set fill color (?).
   *
   * @param color
   */
  public void setPaintFillColor(int color) {
    this.paintFillColor = color;
  }

  /**
   * This method is getter for stroke width.
   *
   * @return
   */
  public float getPaintStrokeWidth() {
    return this.paintStrokeWidth;
  }

  /**
   * This method is setter for stroke width.
   *
   * @param width
   */
  public void setPaintStrokeWidth(float width) {
    if (width >= 0) {
      this.paintStrokeWidth = width;
    } else {
      this.paintStrokeWidth = 3F;
    }
  }

  /**
   * This method is getter for alpha.
   *
   * @return
   */
  public int getOpacity() {
    return this.opacity;
  }

  /**
   * This method is setter for alpha.
   * The 1st argument must be between 0 and 255.
   *
   * @param opacity
   */
  public void setOpacity(int opacity) {
    if ((opacity >= 0) && (opacity <= 255)) {
      this.opacity = opacity;
    } else {
      this.opacity= 255;
    }
  }

  /**
   * This method is getter for amount of blur.
   *
   * @return
   */
  public float getBlur() {
    return this.blur;
  }

  /**
   * This method is setter for amount of blur.
   * The 1st argument is greater than or equal to 0.0.
   *
   * @param blur
   */
  public void setBlur(float blur) {
    if (blur >= 0) {
      this.blur = blur;
    } else {
      this.blur = 0F;
    }
  }

  /**
   * This method is getter for line cap.
   *
   * @return
   */
  public Paint.Cap getLineCap() {
    return this.lineCap;
  }

  /**
   * This method is setter for line cap.
   *
   * @param cap
   */
  public void setLineCap(Paint.Cap cap) {
    this.lineCap = cap;
  }

  /**
   * This method gets current canvas as bitmap.
   *
   * @return This is returned as bitmap.
   */
  public Bitmap getBitmap() {
    this.setDrawingCacheEnabled(false);
    this.setDrawingCacheEnabled(true);

    return Bitmap.createBitmap(this.getDrawingCache());
  }

  /**
   * This method gets current canvas as scaled bitmap.
   *
   * @return This is returned as scaled bitmap.
   */
  public Bitmap getScaleBitmap(int w, int h) {
    this.setDrawingCacheEnabled(false);
    this.setDrawingCacheEnabled(true);

    return Bitmap.createScaledBitmap(this.getDrawingCache(), w, h, true);
  }

  /**
   * This method draws the designated bitmap to canvas.
   *
   * @param bitmap
   */
  public void drawBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
    this.invalidate();
  }

  /**
   * This method draws the designated byte array of bitmap to canvas.
   *
   * @param byteArray This is returned as byte array of bitmap.
   */
  public void drawBitmap(byte[] byteArray) {
    this.drawBitmap(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length));
  }

  /**
   * This static method gets the designated bitmap as byte array.
   *
   * @param bitmap
   * @param format
   * @param quality
   * @return This is returned as byte array of bitmap.
   */
  public static byte[] getBitmapAsByteArray(Bitmap bitmap, CompressFormat format, int quality) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    bitmap.compress(format, quality, byteArrayOutputStream);

    return byteArrayOutputStream.toByteArray();
  }

  /**
   * This method gets the bitmap as byte array.
   *
   * @param format
   * @param quality
   * @return This is returned as byte array of bitmap.
   */
  public byte[] getBitmapAsByteArray(CompressFormat format, int quality) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    this.getBitmap().compress(format, quality, byteArrayOutputStream);

    return byteArrayOutputStream.toByteArray();
  }

  /**
   * This method gets the bitmap as byte array.
   * Bitmap format is PNG, and quality is 100.
   *
   * @return This is returned as byte array of bitmap.
   */
  public byte[] getBitmapAsByteArray() {
    return this.getBitmapAsByteArray(CompressFormat.PNG, 100);
  }

  public @NonNull Set<Integer> getUniqueColors() {
    Set<Integer> colors = new LinkedHashSet<>();

    for (int i = 1; i < paintLists.size() && i < historyPointer; i++) {
      int color = paintLists.get(i).getColor();
      colors.add(Color.rgb(Color.red(color), Color.green(color), Color.blue(color)));
    }

    return colors;
  }
}