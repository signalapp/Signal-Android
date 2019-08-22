package org.thoughtcrime.securesms.imageeditor.model;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.renderers.CropAreaRenderer;
import org.thoughtcrime.securesms.imageeditor.renderers.InverseFillRenderer;

/**
 * Creates and handles a strict EditorElement Hierarchy.
 * <p>
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
 * |     |  |  |  |- thumbs
 * |     |  |  |  |  |- Center left thumb
 * |     |  |  |  |  |- Center right thumb
 * |     |  |  |  |  |- Top center thumb
 * |     |  |  |  |  |- Bottom center thumb
 * |     |  |  |  |  |- Top left thumb
 * |     |  |  |  |  |- Top right thumb
 * |     |  |  |  |  |- Bottom left thumb
 * |     |  |  |  |  |- Bottom right thumb
 */
final class EditorElementHierarchy {

  static @NonNull EditorElementHierarchy create() {
    return new EditorElementHierarchy(createRoot());
  }

  static @NonNull EditorElementHierarchy create(@Nullable EditorElement root) {
    if (root == null) {
      return create();
    } else {
      return new EditorElementHierarchy(root);
    }
  }

  private final EditorElement root;
  private final EditorElement view;
  private final EditorElement flipRotate;
  private final EditorElement imageRoot;
  private final EditorElement overlay;
  private final EditorElement imageCrop;
  private final EditorElement cropEditorElement;
  private final EditorElement blackout;
  private final EditorElement thumbs;

  private EditorElementHierarchy(@NonNull EditorElement root) {
    this.root              = root;
    this.view              = this.root.getChild(0);
    this.flipRotate        = this.view.getChild(0);
    this.imageRoot         = this.flipRotate.getChild(0);
    this.overlay           = this.flipRotate.getChild(1);
    this.imageCrop         = this.overlay.getChild(0);
    this.cropEditorElement = this.imageCrop.getChild(0);
    this.blackout          = this.cropEditorElement.getChild(0);
    this.thumbs            = this.cropEditorElement.getChild(1);
  }

  private static @NonNull EditorElement createRoot() {
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

    EditorElement cropEditorElement = new EditorElement(new CropAreaRenderer(R.color.crop_area_renderer_outer_color));

    cropEditorElement.getFlags()
                     .setRotateLocked(true)
                     .setAspectLocked(true)
                     .setSelectable(false)
                     .setVisible(false)
                     .persist();

    imageCrop.addElement(cropEditorElement);

    EditorElement blackout = new EditorElement(new InverseFillRenderer(0xff000000));

    blackout.getFlags()
            .setSelectable(false)
            .setEditable(false)
            .persist();

    cropEditorElement.addElement(blackout);

    cropEditorElement.addElement(createThumbs(cropEditorElement));
    return root;
  }

  private static @NonNull EditorElement createThumbs(EditorElement cropEditorElement) {
    EditorElement thumbs = new EditorElement(null);

    thumbs.getFlags()
          .setChildrenVisible(false)
          .setSelectable(false)
          .setVisible(false)
          .persist();

    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.CENTER_LEFT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.CENTER_RIGHT));

    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_CENTER));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_CENTER));

    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_LEFT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.TOP_RIGHT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_LEFT));
    thumbs.addElement(newThumb(cropEditorElement, ThumbRenderer.ControlPoint.BOTTOM_RIGHT));

    return thumbs;
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

  void startCrop(@NonNull Runnable invalidate) {
    Matrix editor         = new Matrix();
    float  scaleInForCrop = 0.8f;

    editor.postScale(scaleInForCrop, scaleInForCrop);
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

  void flipRotate(int degrees, int scaleX, int scaleY, @NonNull RectF visibleViewPort, @Nullable Runnable invalidate) {
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
