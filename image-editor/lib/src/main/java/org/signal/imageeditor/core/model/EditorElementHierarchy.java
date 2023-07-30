package org.signal.imageeditor.core.model;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.R;
import org.signal.imageeditor.core.SelectableRenderer;
import org.signal.imageeditor.core.renderers.CropAreaRenderer;
import org.signal.imageeditor.core.renderers.FillRenderer;
import org.signal.imageeditor.core.renderers.InverseFillRenderer;
import org.signal.imageeditor.core.renderers.OvalGuideRenderer;
import org.signal.imageeditor.core.renderers.SelectedElementGuideRenderer;
import org.signal.imageeditor.core.renderers.TrashRenderer;

/**
 * Creates and handles a strict EditorElement Hierarchy.
 * <p>
 * <pre>
 * root - always square, contains only temporary zooms for editing. e.g. when the whole editor zooms out for cropping
 * |
 * |- view - contains persisted adjustments for crops
 * |  |
 * |  |- flipRotate - contains persisted adjustments for flip and rotate operations, ensures operations are centered within the current view
 * |     |
 * |     |- imageRoot
 * |     |  |- mainImage
 * |     |     |- stickers/drawings/text
 * |     |
 * |     |- overlay - always square
 * |     |  |- imageCrop - a crop to match the aspect of the main image
 * |     |  |  |- cropEditorElement - user crop, not always square, but upright, the area of the view
 * |     |  |  |  |  All children do not move/scale or rotate.
 * |     |  |  |  |- blackout
 * |     |  |  |  |- fade
 * |     |  |  |  |- thumbs
 * |     |  |  |  |  |- Center left thumb
 * |     |  |  |  |  |- Center right thumb
 * |     |  |  |  |  |- Top center thumb
 * |     |  |  |  |  |- Bottom center thumb
 * |     |  |  |  |  |- Top left thumb
 * |     |  |  |  |  |- Top right thumb
 * |     |  |  |  |  |- Bottom left thumb
 * |     |  |  |  |  |- Bottom right thumb
 * |     |  |- selection - matches the aspect and overall matrix of the selected item's selectedBounds
 * |     |  |  |- Selection thumbs
 * </pre>
 */
final class EditorElementHierarchy {

  static @NonNull EditorElementHierarchy create(@ColorInt int blackoutColor) {
    return new EditorElementHierarchy(createRoot(CropStyle.RECTANGLE, blackoutColor));
  }

  static @NonNull EditorElementHierarchy createForCircleEditing(@ColorInt int blackoutColor) {
    return new EditorElementHierarchy(createRoot(CropStyle.CIRCLE, blackoutColor));
  }

  static @NonNull EditorElementHierarchy createForPinchAndPanCropping(@ColorInt int blackoutColor) {
    return new EditorElementHierarchy(createRoot(CropStyle.PINCH_AND_PAN, blackoutColor));
  }

  static @NonNull EditorElementHierarchy create(@NonNull EditorElement root) {
    return new EditorElementHierarchy(root);
  }

  private final EditorElement root;
  private final EditorElement view;
  private final EditorElement flipRotate;
  private final EditorElement imageRoot;
  private final EditorElement overlay;
  private final EditorElement imageCrop;
  private final EditorElement cropEditorElement;
  private final EditorElement blackout;
  private final EditorElement fade;
  private final EditorElement trash;
  private final EditorElement thumbs;
  private final EditorElement selection;

  private EditorElement selectedElement;

  private EditorElementHierarchy(@NonNull EditorElement root) {
    this.root              = root;
    this.view              = this.root.getChild(0);
    this.flipRotate        = this.view.getChild(0);
    this.imageRoot         = this.flipRotate.getChild(0);
    this.overlay           = this.flipRotate.getChild(1);
    this.imageCrop         = this.overlay.getChild(0);
    this.selection         = this.overlay.getChild(1);
    this.cropEditorElement = this.imageCrop.getChild(0);
    this.blackout          = this.cropEditorElement.getChild(0);
    this.thumbs            = this.cropEditorElement.getChild(1);
    this.fade              = this.cropEditorElement.getChild(2);
    this.trash             = this.cropEditorElement.getChild(3);
  }

  private enum CropStyle {
    /**
     * A rectangular overlay with 8 thumbs, corners and edges.
     */
    RECTANGLE,

    /**
     * Cropping with a circular template overlay with Corner thumbs only.
     */
    CIRCLE,

    /**
     * No overlay and no thumbs. Cropping achieved through pinching and panning.
     */
    PINCH_AND_PAN
  }

  private static @NonNull EditorElement createRoot(@NonNull CropStyle cropStyle, @ColorInt int blackoutColor) {
    EditorElement root = new EditorElement(null);

    EditorElement imageRoot = new EditorElement(null);
    root.addElement(imageRoot);

    EditorElement flipRotate = new EditorElement(null);
    imageRoot.addElement(flipRotate);

    EditorElement image = new EditorElement(null);
    flipRotate.addElement(image);

    EditorElement overlay = new EditorElement(null);
    flipRotate.addElement(overlay);

    EditorElement imageCrop = new EditorElement(null);
    overlay.addElement(imageCrop);

    EditorElement selection = new EditorElement(null);
    overlay.addElement(selection);

    boolean       renderCenterThumbs = cropStyle == CropStyle.RECTANGLE;
    EditorElement cropEditorElement  = new EditorElement(new CropAreaRenderer(ColorUtils.setAlphaComponent(blackoutColor, 0x7F), renderCenterThumbs));

    cropEditorElement.getFlags()
                     .setRotateLocked(true)
                     .setAspectLocked(true)
                     .setSelectable(false)
                     .setVisible(false)
                     .persist();

    imageCrop.addElement(cropEditorElement);


    EditorElement fade = new EditorElement(new FillRenderer(ColorUtils.setAlphaComponent(blackoutColor, 0x66)), EditorModel.Z_FADE);
    fade.getFlags()
        .setSelectable(false)
        .setEditable(false)
        .setVisible(false)
        .persist();
    cropEditorElement.addElement(fade);

    EditorElement trash = new EditorElement(new TrashRenderer(), EditorModel.Z_TRASH);
    trash.getFlags()
         .setSelectable(false)
         .setEditable(false)
         .setVisible(false)
         .persist();
    cropEditorElement.addElement(trash);

    EditorElement blackout = new EditorElement(new InverseFillRenderer(ColorUtils.setAlphaComponent(blackoutColor, 0xFF)));

    blackout.getFlags()
            .setSelectable(false)
            .setEditable(false)
            .persist();

    cropEditorElement.addElement(blackout);

    if (cropStyle == CropStyle.PINCH_AND_PAN) {
      cropEditorElement.addElement(new EditorElement(null));
    } else {
      cropEditorElement.addElement(createThumbs(cropEditorElement, renderCenterThumbs));

      if (cropStyle == CropStyle.CIRCLE) {
        EditorElement circle = new EditorElement(new OvalGuideRenderer(R.color.crop_circle_guide_color), EditorModel.Z_CIRCLE);
        circle.getFlags().setSelectable(false)
              .persist();

        cropEditorElement.addElement(circle);
      }
    }

    return root;
  }

  private static @NonNull EditorElement createThumbs(EditorElement cropEditorElement, boolean centerThumbs) {
    EditorElement thumbs = new EditorElement(null);

    thumbs.getFlags()
          .setChildrenVisible(false)
          .setSelectable(false)
          .setVisible(false)
          .persist();

    if (centerThumbs) {
      thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.CENTER_LEFT));
      thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.CENTER_RIGHT));

      thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_CENTER));
      thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_CENTER));
    }

    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_LEFT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_RIGHT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_LEFT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_RIGHT));

    return thumbs;
  }

  void removeAllSelectionArtifacts() {
    selection.deleteAllChildren();
    selectedElement = null;
  }

  void updateSelectionThumbsForElement(@NonNull EditorElement element, @Nullable Matrix overlayMappingMatrix) {
    if (element == selectedElement) {
      setOrUpdateSelectionThumbsForElement(element, overlayMappingMatrix);
    }
  }

  void setOrUpdateSelectionThumbsForElement(@NonNull EditorElement element, @Nullable Matrix overlayMappingMatrix) {
    if (selectedElement != element) {
      removeAllSelectionArtifacts();

      if (element.getRenderer() instanceof SelectableRenderer) {
        selectedElement = element;
      } else {
        selectedElement = null;
      }

      if (selectedElement == null) return;

      selection.addElement(createSelectionBox());
      selection.addElement(createScaleControlThumb(element));
      selection.addElement(createRotateControlThumb(element));
    }

    if (overlayMappingMatrix != null) {
      Matrix selectionMatrix = selection.getLocalMatrix();

      if (selectedElement.getRenderer() instanceof SelectableRenderer) {
        SelectableRenderer renderer = (SelectableRenderer) selectedElement.getRenderer();
        RectF              bounds   = new RectF();
        renderer.getSelectionBounds(bounds);
        selectionMatrix.setRectToRect(Bounds.FULL_BOUNDS, bounds, Matrix.ScaleToFit.FILL);
      }

      selectionMatrix.postConcat(overlayMappingMatrix);
    }
  }

  private static @NonNull EditorElement createSelectionBox() {
    return new EditorElement(new SelectedElementGuideRenderer());
  }

  private static @NonNull EditorElement createScaleControlThumb(@NonNull EditorElement element) {
    ThumbRenderer.ControlPoint controlPoint = ThumbRenderer.ControlPoint.SCALE_ROT_RIGHT;
    EditorElement              thumbElement = new EditorElement(new CropThumbRenderer(controlPoint, element.getId()));
    thumbElement.getLocalMatrix().preTranslate(controlPoint.getX(), controlPoint.getY());
    return thumbElement;
  }

  private static @NonNull EditorElement createRotateControlThumb(@NonNull EditorElement element) {
    ThumbRenderer.ControlPoint controlPoint       = ThumbRenderer.ControlPoint.SCALE_ROT_LEFT;
    EditorElement              rotateThumbElement = new EditorElement(new CropThumbRenderer(controlPoint, element.getId()));
    rotateThumbElement.getLocalMatrix().preTranslate(controlPoint.getX(), controlPoint.getY());
    return rotateThumbElement;
  }

  private static @NonNull EditorElement newThumb(@NonNull EditorElement toControl, @NonNull ThumbRenderer.ControlPoint controlPoint) {
    EditorElement element = new EditorElement(new CropThumbRenderer(controlPoint, toControl.getId()));

    element.getFlags()
           .setSelectable(false)
           .persist();

    element.getLocalMatrix().preTranslate(controlPoint.getX(), controlPoint.getY());

    return element;
  }

  EditorElement getRoot() {
    return root;
  }

  EditorElement getImageRoot() {
    return imageRoot;
  }

  EditorElement getSelection() {
    return selection;
  }

  public @Nullable EditorElement getSelectedElement() {
    return selectedElement;
  }

  EditorElement getTrash() {
    return trash;
  }

  /**
   * The main image, null if not yet set.
   */
  @Nullable EditorElement getMainImage() {
    return imageRoot.getChildCount() > 0 ? imageRoot.getChild(0) : null;
  }

  EditorElement getCropEditorElement() {
    return cropEditorElement;
  }

  EditorElement getImageCrop() {
    return imageCrop;
  }

  EditorElement getOverlay() {
    return overlay;
  }

  EditorElement getFlipRotate() {
    return flipRotate;
  }

  void addFade(@NonNull Runnable invalidate) {
    fade.getFlags()
        .setVisible(true)
        .persist();

    invalidate.run();
  }

  void removeFade(@NonNull Runnable invalidate) {
    fade.getFlags()
        .setVisible(false)
        .persist();

    invalidate.run();
  }

  /**
   * @param scaleIn Use 1 for no scale in, use less than 1 and it will zoom the image out
   *                so user can see more of the surrounding image while cropping.
   */
  void startCrop(@NonNull Runnable invalidate, float scaleIn) {
    Matrix editor = new Matrix();

    editor.postScale(scaleIn, scaleIn);
    root.animateEditorTo(editor, invalidate);

    cropEditorElement.getFlags()
                     .setVisible(true);

    blackout.getFlags()
            .setVisible(false);

    thumbs.getFlags()
          .setChildrenVisible(true);

    thumbs.forAllInTree(element -> element.getFlags().setSelectable(true));

    imageRoot.forAllInTree(element -> element.getFlags().setSelectable(false));

    EditorElement mainImage = getMainImage();
    if (mainImage != null) {
      mainImage.getFlags().setSelectable(true);
    }

    invalidate.run();
  }

  void doneCrop(@NonNull RectF visibleViewPort, @Nullable Runnable invalidate) {
    updateViewToCrop(visibleViewPort, invalidate);

    root.rollbackEditorMatrix(invalidate);

    root.forAllInTree(element -> element.getFlags().reset());
  }

  void updateViewToCrop(@NonNull RectF visibleViewPort, @Nullable Runnable invalidate) {
    RectF dst = new RectF();

    getCropFinalMatrix().mapRect(dst, Bounds.FULL_BOUNDS);

    Matrix temp = new Matrix();
    temp.setRectToRect(dst, visibleViewPort, Matrix.ScaleToFit.CENTER);
    view.animateLocalTo(temp, invalidate);
  }

  private @NonNull Matrix getCropFinalMatrix() {
    Matrix matrix = new Matrix(flipRotate.getLocalMatrix());
    matrix.preConcat(imageCrop.getLocalMatrix());
    matrix.preConcat(cropEditorElement.getLocalMatrix());
    return matrix;
  }

  /**
   * Returns a matrix that maps points from the crop on to the visible image.
   * <p>
   * i.e. if a mapped point is in bounds, then the point is on the visible image.
   */
  @Nullable Matrix imageMatrixRelativeToCrop() {
    EditorElement mainImage = getMainImage();
    if (mainImage == null) return null;

    Matrix matrix1 = new Matrix(imageCrop.getLocalMatrix());
    matrix1.preConcat(cropEditorElement.getLocalMatrix());
    matrix1.preConcat(cropEditorElement.getEditorMatrix());

    Matrix matrix2 = new Matrix(mainImage.getLocalMatrix());
    matrix2.preConcat(mainImage.getEditorMatrix());
    matrix2.preConcat(imageCrop.getLocalMatrix());

    Matrix inverse = new Matrix();
    matrix2.invert(inverse);
    inverse.preConcat(matrix1);

    return inverse;
  }

  void dragDropRelease(@NonNull RectF visibleViewPort, @NonNull Runnable invalidate) {
    if (cropEditorElement.getFlags().isVisible()) {
      updateViewToCrop(visibleViewPort, invalidate);
    }
  }

  RectF getCropRect() {
    RectF dst = new RectF();
    getCropFinalMatrix().mapRect(dst, Bounds.FULL_BOUNDS);
    return dst;
  }

  void flipRotate(float degrees, int scaleX, int scaleY, @NonNull RectF visibleViewPort, @Nullable Runnable invalidate) {
    Matrix newLocal = new Matrix(flipRotate.getLocalMatrix());
    if (degrees != 0) {
      newLocal.postRotate(degrees);
    }
    newLocal.postScale(scaleX, scaleY);
    flipRotate.animateLocalTo(newLocal, invalidate);
    updateViewToCrop(visibleViewPort, invalidate);
  }

  /**
   * The full matrix for the {@link #getMainImage()} from {@link #root} down.
   */
  Matrix getMainImageFullMatrix() {
    Matrix matrix = new Matrix();

    matrix.preConcat(view.getLocalMatrix());
    matrix.preConcat(getMainImageFullMatrixFromFlipRotate());

    return matrix;
  }

   /**
   * The full matrix for the {@link #getMainImage()} from {@link #flipRotate} down.
   */
  Matrix getMainImageFullMatrixFromFlipRotate() {
    Matrix matrix = new Matrix();

    matrix.preConcat(flipRotate.getLocalMatrix());
    matrix.preConcat(imageRoot.getLocalMatrix());

    EditorElement mainImage = getMainImage();
    if (mainImage != null) {
      matrix.preConcat(mainImage.getLocalMatrix());
    }

    return matrix;
  }

  /**
   * Calculates the exact output size based upon the crops/rotates and zooms in the hierarchy.
   *
   * @param inputSize Main image size
   * @return Size after applying all zooms/rotates and crops
   */
  PointF getOutputSize(@NonNull Point inputSize) {
    Matrix matrix = new Matrix();

    matrix.preConcat(flipRotate.getLocalMatrix());
    matrix.preConcat(cropEditorElement.getLocalMatrix());
    matrix.preConcat(cropEditorElement.getEditorMatrix());
    EditorElement mainImage = getMainImage();
    if (mainImage != null) {
      float xScale = 1f / (xScale(mainImage.getLocalMatrix()) * xScale(mainImage.getEditorMatrix()));
      matrix.preScale(xScale, xScale);
    }

    float[] dst = new float[4];
    matrix.mapPoints(dst, new float[]{ 0, 0, inputSize.x, inputSize.y });

    float widthF  = Math.abs(dst[0] - dst[2]);
    float heightF = Math.abs(dst[1] - dst[3]);

    return new PointF(widthF, heightF);
  }

  /**
   * Extract the x scale from a matrix, which is the length of the first column.
   */
  static float xScale(@NonNull Matrix matrix) {
    float[] values = new float[9];
    matrix.getValues(values);
    return (float) Math.sqrt(values[0] * values[0] + values[3] * values[3]);
  }
}
