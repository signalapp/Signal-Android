package org.signal.imageeditor.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import org.signal.imageeditor.R;
import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.signal.imageeditor.core.model.ThumbRenderer;
import org.signal.imageeditor.core.renderers.BezierDrawingRenderer;
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer;
import org.signal.imageeditor.core.renderers.TrashRenderer;

import java.util.LinkedList;
import java.util.List;

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

  private static final int DEFAULT_BLACKOUT_COLOR = 0xFF000000;

  /** Maximum distance squared a user can move the pointer before we consider a drag starting */
  private static final int MAX_MOVE_SQUARED_BEFORE_DRAG = 10;

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
  private SizeChangedListener sizeChangedListener;

  @Nullable
  private UndoRedoStackListener undoRedoStackListener;

  @Nullable
  private DragListener dragListener;

  private final List<HiddenEditText.TextFilter> textFilters = new LinkedList<>();

  private final Matrix viewMatrix      = new Matrix();
  private final RectF  viewPort        = Bounds.newFullBounds();
  private final RectF  visibleViewPort = Bounds.newFullBounds();
  private final RectF  screen          = new RectF();

  private TapListener                      tapListener;
  private RendererContext                  rendererContext;
  private RendererContext.TypefaceProvider typefaceProvider;

  @Nullable
  private EditSession editSession;
  private boolean     moreThanOnePointerUsedInSession;
  private PointF      touchDownStart;

  private boolean inDrag;

  public ImageEditorView(Context context) {
    super(context);
    init(null);
  }

  public ImageEditorView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public ImageEditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attributeSet) {
    setWillNotDraw(false);

    final int blackoutColor;
    if (attributeSet != null) {
      TypedArray typedArray = getContext().obtainStyledAttributes(attributeSet, R.styleable.ImageEditorView);
      blackoutColor = typedArray.getColor(R.styleable.ImageEditorView_imageEditorView_blackoutColor, DEFAULT_BLACKOUT_COLOR);
      typedArray.recycle();
    } else {
      blackoutColor = DEFAULT_BLACKOUT_COLOR;
    }

    setModel(EditorModel.create(blackoutColor));

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
    editText.addTextFilters(textFilters);

    return editText;
  }

  public void startTextEditing(@NonNull EditorElement editorElement) {
    getModel().addFade();
    if (editorElement.getRenderer() instanceof MultiLineTextRenderer) {
      getModel().setSelectionVisible(false);
      editText.setCurrentTextEditorElement(editorElement);
    }
  }

  public void zoomToFitText(@NonNull EditorElement editorElement, @NonNull MultiLineTextRenderer textRenderer) {
      getModel().zoomToTextElement(editorElement, textRenderer);
  }

  public boolean isTextEditing() {
    return editText.getCurrentTextEntity() != null;
  }

  public void doneTextEditing() {
    getModel().zoomOut();
    getModel().removeFade();
    getModel().setSelectionVisible(true);
    if (editText.getCurrentTextEntity() != null) {
      getModel().setSelected(null);
      editText.setCurrentTextEditorElement(null);
      editText.hideKeyboard();
    }
  }

  public void setTypefaceProvider(@NonNull RendererContext.TypefaceProvider typefaceProvider) {
    this.typefaceProvider = typefaceProvider;
  }

  public void addTextInputFilter(@NonNull HiddenEditText.TextFilter inputFilter) {
    textFilters.add(inputFilter);
    editText = createAHiddenTextEntryField();
  }

  public void removeTextInputFilter(@NonNull HiddenEditText.TextFilter inputFilter) {
    textFilters.remove(inputFilter);
    editText = createAHiddenTextEntryField();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (rendererContext == null || rendererContext.canvas != canvas || rendererContext.typefaceProvider != typefaceProvider) {
      rendererContext = new RendererContext(getContext(), canvas, rendererReady, rendererInvalidate, typefaceProvider);
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
    if (sizeChangedListener != null) {
      sizeChangedListener.onSizeChanged(w, h);
    }
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

        inDrag = false;
        moreThanOnePointerUsedInSession = false;
        touchDownStart = point;
        model.pushUndoPoint();
        editSession = startEdit(inverse, point, selected);

        if (editSession != null) {
          checkTrashIntersect(point);
        }

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
          if (inDrag) {
            notifyDragMove(editSession.getSelected(), checkTrashIntersect(getPoint(event)));
          } else if (pointerCount == 1) {
            checkDragStart(event);
          }
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
            dragDropRelease(false);
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_POINTER_UP: {
        if (editSession != null && event.getActionIndex() < 2) {
          editSession.commit();
          model.pushUndoPoint();
          dragDropRelease(true);

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
          dragDropRelease(false);

          PointF  point        = getPoint(event);
          boolean hittingTrash = event.getPointerCount() == 1 &&
                                 checkTrashIntersect(point)   &&
                                 model.findElementAtPoint(point, viewMatrix, new Matrix()) == editSession.getSelected();

          if (inDrag) {
            notifyDragEnd(editSession.getSelected(), hittingTrash);
            inDrag = false;
          }

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

  private boolean checkTrashIntersect(@NonNull PointF point) {
    if (mode == Mode.Draw || mode == Mode.Blur) {
      return false;
    }

    if (model.checkTrashIntersectsPoint(point)) {
      if (model.getTrash().getRenderer() instanceof TrashRenderer) {
        ((TrashRenderer) model.getTrash().getRenderer()).expand();
      }
      return true;
    } else {
      if (model.getTrash().getRenderer() instanceof TrashRenderer) {
        ((TrashRenderer) model.getTrash().getRenderer()).shrink();
      }
      return false;
    }
  }

  private void checkDragStart(MotionEvent moveEvent) {
    if (inDrag || editSession == null) {
      return;
    }
    float dX = touchDownStart.x - moveEvent.getX();
    float dY = touchDownStart.y - moveEvent.getY();

    float distSquared = dX * dX + dY * dY;
    if (distSquared > MAX_MOVE_SQUARED_BEFORE_DRAG) {
      inDrag = true;
      notifyDragStart(editSession.getSelected());
    }
  }

  private void notifyDragStart(@Nullable EditorElement editorElement) {
    if (dragListener != null) {
      dragListener.onDragStarted(editorElement);
    }
  }

  private void notifyDragMove(@Nullable EditorElement editorElement, boolean isInTrashHitZone) {
    if (dragListener != null) {
      dragListener.onDragMoved(editorElement, isInTrashHitZone);
    }
  }

  private void notifyDragEnd(@Nullable EditorElement editorElement, boolean isInTrashHitZone) {
    if (dragListener != null) {
      dragListener.onDragEnded(editorElement, isInTrashHitZone);
    }
  }

  private @Nullable EditSession startEdit(@NonNull Matrix inverse, @NonNull PointF point, @Nullable EditorElement selected) {
    EditSession editSession = startAMoveAndResizeSession(inverse, point, selected);
    if (editSession == null && (mode == Mode.Draw || mode == Mode.Blur)) {
      return startADrawingSession(point);
    } else {
      setMode(Mode.MoveAndResize);
      return editSession;
    }
  }

  private EditSession startADrawingSession(@NonNull PointF point) {
    BezierDrawingRenderer renderer = new BezierDrawingRenderer(color, thickness * Bounds.FULL_BOUNDS.width(), cap, model.findCropRelativeToRoot());
    EditorElement element          = new EditorElement(renderer, mode == Mode.Blur ? EditorModel.Z_MASK : EditorModel.Z_DRAWING);
    model.addElementCentered(element, 1);

    Matrix elementInverseMatrix = model.findElementInverseMatrix(element, viewMatrix);

    return DrawingSession.start(element, renderer, elementInverseMatrix, point);
  }

  private EditSession startAMoveAndResizeSession(@NonNull Matrix inverse, @NonNull PointF point, @Nullable EditorElement selected) {
    Matrix elementInverseMatrix;
    if (selected == null) return null;

    if (selected.getRenderer() instanceof ThumbRenderer) {
      ThumbRenderer thumb = (ThumbRenderer) selected.getRenderer();

      EditorElement thumbControlledElement = getModel().findById(thumb.getElementToControl());
      if (thumbControlledElement == null) return null;

      EditorElement thumbsParent = getModel().getRoot().findParent(selected);

      if (thumbsParent == null) return null;

      Matrix thumbContainerRelativeMatrix = model.findRelativeMatrix(thumbsParent, thumbControlledElement);

      if (thumbContainerRelativeMatrix == null) return null;

      selected = thumbControlledElement;

      elementInverseMatrix = model.findElementInverseMatrix(selected, viewMatrix);
      if (elementInverseMatrix != null) {
        return ThumbDragEditSession.startDrag(selected, elementInverseMatrix, thumbContainerRelativeMatrix, thumb.getControlPoint(), point);
      } else {
        return null;
      }
    }

    return ElementDragEditSession.startDrag(selected, inverse, point);
  }

  @NonNull
  public Mode getMode() {
    return mode;
  }

  public void setMode(@NonNull Mode mode) {
    this.mode = mode;
  }

  public void setMainImageEditorMatrixRotation(float angle, float minScaleDown) {
    model.setMainImageEditorMatrixRotation(angle, minScaleDown);
    invalidate();
  }

  public void startDrawing(float thickness, @NonNull Paint.Cap cap, boolean blur) {
    this.thickness = thickness;
    this.cap       = cap;
    setMode(blur ? Mode.Blur : Mode.Draw);
  }

  public void setDrawingBrushColor(int color) {
    this.color = color;
  }

  private void dragDropRelease(boolean stillTouching) {
    model.dragDropRelease();
    if (drawingChangedListener != null) {
      drawingChangedListener.onDrawingChanged(stillTouching);
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

  public void setSizeChangedListener(@Nullable SizeChangedListener sizeChangedListener) {
    this.sizeChangedListener = sizeChangedListener;
  }

  public void setUndoRedoStackListener(@Nullable UndoRedoStackListener undoRedoStackListener) {
    this.undoRedoStackListener = undoRedoStackListener;
  }

  public void setDragListener(@Nullable DragListener dragListener) {
    this.dragListener = dragListener;
  }

  public void setTapListener(TapListener tapListener) {
    this.tapListener = tapListener;
  }

  public void deleteElement(@Nullable EditorElement editorElement) {
    if (editorElement != null) {
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
          model.setSelected(selected);
          tapListener.onEntitySingleTap(selected);
        } else {
          tapListener.onEntitySingleTap(null);
          model.setSelected(null);
        }
        return true;
      }
      return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return false;
    }
  }

  private boolean allowTaps() {
    return !model.isCropping() && mode != Mode.Draw && mode != Mode.Blur;
  }

  public enum Mode {
    MoveAndResize,
    Draw,
    Blur
  }

  public interface DrawingChangedListener {
    void onDrawingChanged(boolean stillTouching);
  }

  public interface SizeChangedListener {
    void onSizeChanged(int newWidth, int newHeight);
  }

  public interface DragListener {
    void onDragStarted(@Nullable EditorElement editorElement);
    void onDragMoved(@Nullable EditorElement editorElement, boolean isInTrashHitZone);
    void onDragEnded(@Nullable EditorElement editorElement, boolean isInTrashHitZone);
  }

  public interface TapListener {

    void onEntityDown(@Nullable EditorElement editorElement);

    void onEntitySingleTap(@Nullable EditorElement editorElement);

    void onEntityDoubleTap(@NonNull EditorElement editorElement);
  }
}
