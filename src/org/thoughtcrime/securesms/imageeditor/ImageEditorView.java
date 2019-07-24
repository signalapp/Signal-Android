package org.thoughtcrime.securesms.imageeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.imageeditor.model.ThumbRenderer;
import org.thoughtcrime.securesms.imageeditor.renderers.BezierDrawingRenderer;
import org.thoughtcrime.securesms.imageeditor.renderers.MultiLineTextRenderer;

/**
 * ImageEditorView
 * <p>
 * Android {@link android.view.View} that allows manipulation of a base image, rotate/flip/crop and
 * addition and manipulation of text/drawing/and other image layers that move with the base image.
 * <p>
 * Drawing
 * <p>
 * Drawing is achieved by setting the {@link #color} and putting the view in {@link Mode#Draw}.
 * Touch events are then passed to a new {@link BezierDrawingRenderer} on a new {@link EditorElement}.
 * <p>
 * New images
 * <p>
 * To add new images to the base image add via the {@link EditorModel#addElementCentered(EditorElement, float)}
 * which centers the new item in the current crop area.
 */
public final class ImageEditorView extends FrameLayout {

  private HiddenEditText editText;

  @NonNull
  private Mode mode = Mode.MoveAndResize;

  @ColorInt
  private int color = 0xff000000;

  private float thickness = 0.02f;

  @NonNull
  private Paint.Cap cap = Paint.Cap.ROUND;

  private EditorModel model;

  private GestureDetectorCompat doubleTap;

  @Nullable
  private DrawingChangedListener drawingChangedListener;

  @Nullable
  private UndoRedoStackListener undoRedoStackListener;

  private final Matrix viewMatrix      = new Matrix();
  private final RectF  viewPort        = Bounds.newFullBounds();
  private final RectF  visibleViewPort = Bounds.newFullBounds();
  private final RectF  screen          = new RectF();

  private TapListener     tapListener;
  private RendererContext rendererContext;

  @Nullable
  private EditSession editSession;
  private boolean     moreThanOnePointerUsedInSession;

  public ImageEditorView(Context context) {
    super(context);
    init();
  }

  public ImageEditorView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ImageEditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    setModel(new EditorModel());

    editText = createAHiddenTextEntryField();

    doubleTap = new GestureDetectorCompat(getContext(), new DoubleTapGestureListener());

    setOnTouchListener((v, event) -> doubleTap.onTouchEvent(event));
  }

  private HiddenEditText createAHiddenTextEntryField() {
    HiddenEditText editText = new HiddenEditText(getContext());
    addView(editText);
    editText.clearFocus();
    editText.setOnEndEdit(this::doneTextEditing);
    editText.setOnEditOrSelectionChange(this::zoomToFitText);
    return editText;
  }

  public void startTextEditing(@NonNull EditorElement editorElement, boolean incognitoKeyboardEnabled, boolean selectAll) {
    if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
      editText.setIncognitoKeyboardEnabled(incognitoKeyboardEnabled);
      editText.setCurrentTextEditorElement(editorElement);
      if (selectAll) {
        editText.selectAll();
      }
      editText.requestFocus();
    }
  }

  private void zoomToFitText(@NonNull EditorElement editorElement, @NonNull MultiLineTextRenderer textRenderer) {
      getModel().zoomToTextElement(editorElement, textRenderer);
  }

  public boolean isTextEditing() {
    return editText.getCurrentTextEntity() != null;
  }

  public void doneTextEditing() {
    getModel().zoomOut();
    if (editText.getCurrentTextEntity() != null) {
      editText.setCurrentTextEditorElement(null);
      editText.hideKeyboard();
      if (tapListener != null) {
        tapListener.onEntityDown(null);
      }
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (rendererContext == null || rendererContext.canvas != canvas) {
      rendererContext = new RendererContext(getContext(), canvas, rendererReady, rendererInvalidate);
    }
    rendererContext.save();
    try {
      rendererContext.canvasMatrix.initial(viewMatrix);

      model.draw(rendererContext, editText.getCurrentTextEditorElement());
    } finally {
      rendererContext.restore();
    }
  }

  private final RendererContext.Ready rendererReady = new RendererContext.Ready() {
    @Override
    public void onReady(@NonNull Renderer renderer, @Nullable Matrix cropMatrix, @Nullable Point size) {
      model.onReady(renderer, cropMatrix, size);
      invalidate();
    }
  };

  private final RendererContext.Invalidate rendererInvalidate = renderer -> invalidate();

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateViewMatrix();
  }

  private void updateViewMatrix() {
    screen.right = getWidth();
    screen.bottom = getHeight();

    viewMatrix.setRectToRect(viewPort, screen, Matrix.ScaleToFit.FILL);

    float[] values = new float[9];
    viewMatrix.getValues(values);

    float scale = values[0] / values[4];

    RectF tempViewPort = Bounds.newFullBounds();
    if (scale < 1) {
      tempViewPort.top /= scale;
      tempViewPort.bottom /= scale;
    } else {
      tempViewPort.left *= scale;
      tempViewPort.right *= scale;
    }

    visibleViewPort.set(tempViewPort);

    viewMatrix.setRectToRect(visibleViewPort, screen, Matrix.ScaleToFit.CENTER);

    model.setVisibleViewPort(visibleViewPort);

    invalidate();
  }

  public void setModel(@NonNull EditorModel model) {
    if (this.model != model) {
      if (this.model != null) {
        this.model.setInvalidate(null);
        this.model.setUndoRedoStackListener(null);
      }
      this.model = model;
      this.model.setInvalidate(this::invalidate);
      this.model.setUndoRedoStackListener(this::onUndoRedoAvailabilityChanged);
      this.model.setVisibleViewPort(visibleViewPort);
      invalidate();
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        Matrix        inverse  = new Matrix();
        PointF        point    = getPoint(event);
        EditorElement selected = model.findElementAtPoint(point, viewMatrix, inverse);

        moreThanOnePointerUsedInSession = false;
        model.pushUndoPoint();
        editSession = startEdit(inverse, point, selected);

        if (tapListener != null && allowTaps()) {
          if (editSession != null) {
            tapListener.onEntityDown(editSession.getSelected());
          } else {
            tapListener.onEntityDown(null);
          }
        }

        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (editSession != null) {
          int historySize  = event.getHistorySize();
          int pointerCount = Math.min(2, event.getPointerCount());

          for (int h = 0; h < historySize; h++) {
            for (int p = 0; p < pointerCount; p++) {
              editSession.movePoint(p, getHistoricalPoint(event, p, h));
            }
          }

          for (int p = 0; p < pointerCount; p++) {
            editSession.movePoint(p, getPoint(event, p));
          }
          model.moving(editSession.getSelected());
          invalidate();
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (editSession != null && event.getPointerCount() == 2) {
          moreThanOnePointerUsedInSession = true;
          editSession.commit();
          model.pushUndoPoint();

          Matrix newInverse = model.findElementInverseMatrix(editSession.getSelected(), viewMatrix);
          if (newInverse != null) {
            editSession = editSession.newPoint(newInverse, getPoint(event, event.getActionIndex()), event.getActionIndex());
          } else {
            editSession = null;
          }
          if (editSession == null) {
            dragDropRelease();
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (editSession != null && event.getActionIndex() < 2) {
          editSession.commit();
          model.pushUndoPoint();
          dragDropRelease();

          Matrix newInverse = model.findElementInverseMatrix(editSession.getSelected(), viewMatrix);
          if (newInverse != null) {
            editSession = editSession.removePoint(newInverse, event.getActionIndex());
          } else {
            editSession = null;
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (editSession != null) {
          editSession.commit();
          dragDropRelease();

          editSession = null;
          model.postEdit(moreThanOnePointerUsedInSession);
          invalidate();
          return true;
        } else {
          model.postEdit(moreThanOnePointerUsedInSession);
        }
        break;
      }
    }

    return super.onTouchEvent(event);
  }

  private @Nullable EditSession startEdit(@NonNull Matrix inverse, @NonNull PointF point, @Nullable EditorElement selected) {
    if (mode == Mode.Draw) {
      return startADrawingSession(point);
    } else {
      return startAMoveAndResizeSession(inverse, point, selected);
    }
  }

  private EditSession startADrawingSession(@NonNull PointF point) {
    BezierDrawingRenderer renderer = new BezierDrawingRenderer(color, thickness * Bounds.FULL_BOUNDS.width(), cap, model.findCropRelativeToRoot());
    EditorElement element          = new EditorElement(renderer);
    model.addElementCentered(element, 1);

    Matrix elementInverseMatrix = model.findElementInverseMatrix(element, viewMatrix);

    return DrawingSession.start(element, renderer, elementInverseMatrix, point);
  }

  private EditSession startAMoveAndResizeSession(@NonNull Matrix inverse, @NonNull PointF point, @Nullable EditorElement selected) {
    Matrix elementInverseMatrix;
    if (selected == null) return null;

    if (selected.getRenderer() instanceof ThumbRenderer) {
      ThumbRenderer thumb = (ThumbRenderer) selected.getRenderer();

      selected = getModel().findById(thumb.getElementToControl());

      if (selected == null) return null;

      elementInverseMatrix = model.findElementInverseMatrix(selected, viewMatrix);
      if (elementInverseMatrix != null) {
        return ThumbDragEditSession.startDrag(selected, elementInverseMatrix, thumb.getControlPoint(), point);
      } else {
        return null;
      }
    }

    return ElementDragEditSession.startDrag(selected, inverse, point);
  }

  public void setMode(@NonNull Mode mode) {
    this.mode = mode;
  }

  public void startDrawing(float thickness, @NonNull Paint.Cap cap) {
    this.thickness = thickness;
    this.cap       = cap;
    setMode(Mode.Draw);
  }

  public void setDrawingBrushColor(int color) {
    this.color = color;
  }

  private void dragDropRelease() {
    model.dragDropRelease();
    if (drawingChangedListener != null) {
      drawingChangedListener.onDrawingChanged();
    }
  }

  private static PointF getPoint(MotionEvent event) {
    return getPoint(event, 0);
  }

  private static PointF getPoint(MotionEvent event, int p) {
    return new PointF(event.getX(p), event.getY(p));
  }

  private static PointF getHistoricalPoint(MotionEvent event, int p, int historicalIndex) {
    return new PointF(event.getHistoricalX(p, historicalIndex),
                      event.getHistoricalY(p, historicalIndex));
  }

  public EditorModel getModel() {
    return model;
  }

  public void setDrawingChangedListener(@Nullable DrawingChangedListener drawingChangedListener) {
    this.drawingChangedListener = drawingChangedListener;
  }

  public void setUndoRedoStackListener(@Nullable UndoRedoStackListener undoRedoStackListener) {
    this.undoRedoStackListener = undoRedoStackListener;
  }

  public void setTapListener(TapListener tapListener) {
    this.tapListener = tapListener;
  }

  public void deleteElement(@Nullable EditorElement editorElement) {
    if (editorElement != null) {
      model.pushUndoPoint();
      model.delete(editorElement);
      invalidate();
    }
  }

  private void onUndoRedoAvailabilityChanged(boolean undoAvailable, boolean redoAvailable) {
    if (undoRedoStackListener != null) {
      undoRedoStackListener.onAvailabilityChanged(undoAvailable, redoAvailable);
    }
  }

  private final class DoubleTapGestureListener extends GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      if (tapListener != null && editSession != null && allowTaps()) {
        tapListener.onEntityDoubleTap(editSession.getSelected());
      }
      return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      if (tapListener != null && allowTaps()) {
        if (editSession != null) {
          EditorElement selected = editSession.getSelected();
          model.indicateSelected(selected);
          tapListener.onEntitySingleTap(selected);
        } else {
          tapListener.onEntitySingleTap(null);
        }
      }
      return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return false;
    }
  }

  private boolean allowTaps() {
    return !model.isCropping() && mode != Mode.Draw;
  }

  public enum Mode {
    MoveAndResize,
    Draw
  }

  public interface DrawingChangedListener {
    void onDrawingChanged();
  }

  public interface TapListener {

    void onEntityDown(@Nullable EditorElement editorElement);

    void onEntitySingleTap(@Nullable EditorElement editorElement);

    void onEntityDoubleTap(@NonNull EditorElement editorElement);
  }
}
