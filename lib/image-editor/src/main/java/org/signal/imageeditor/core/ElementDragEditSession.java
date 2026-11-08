package org.signal.imageeditor.core;

import android.graphics.Matrix;
import android.graphics.PointF;

import androidx.annotation.NonNull;

import org.signal.imageeditor.core.model.EditorElement;

final class ElementDragEditSession extends ElementEditSession {

  private final RotationSnapListener rotationSnapListener;

  private ElementDragEditSession(@NonNull EditorElement selected, @NonNull Matrix inverseMatrix, @NonNull RotationSnapListener rotationSnapListener) {
    super(selected, inverseMatrix);
    this.rotationSnapListener = rotationSnapListener;
  }

  static ElementDragEditSession startDrag(@NonNull EditorElement selected, @NonNull Matrix inverseViewModelMatrix, @NonNull PointF point, @NonNull RotationSnapListener rotationSnapListener) {
    if (!selected.getFlags().isEditable()) return null;

    ElementDragEditSession elementDragEditSession = new ElementDragEditSession(selected, inverseViewModelMatrix, rotationSnapListener);
    elementDragEditSession.setScreenStartPoint(0, point);
    elementDragEditSession.setScreenEndPoint(0, point);

    return elementDragEditSession;
  }

  @Override
  public void movePoint(int p, @NonNull PointF point) {
    setScreenEndPoint(p, point);

    selected.getEditorMatrix()
            .setTranslate(endPointElement[0].x - startPointElement[0].x, endPointElement[0].y - startPointElement[0].y);
  }

  @Override
  public EditSession newPoint(@NonNull Matrix newInverse, @NonNull PointF point, int p) {
    return ElementScaleEditSession.startScale(this, newInverse, point, p, rotationSnapListener);
  }

  @Override
  public EditSession removePoint(@NonNull Matrix newInverse, int p) {
    return this;
  }
}
