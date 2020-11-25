package org.thoughtcrime.securesms.imageeditor.model;

import android.graphics.Matrix;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An image consists of a tree of {@link EditorElement}s.
 * <p>
 * Each element has some persisted state:
 * - An optional {@link Renderer} so that it can draw itself.
 * - A list of child elements that make the tree possible.
 * - Its own transformation matrix, which applies to itself and all its children.
 * - A set of flags controlling visibility, selectablity etc.
 * <p>
 * Then some temporary state.
 * - A editor matrix for displaying as yet uncommitted edits.
 * - An animation matrix for animating from one matrix to another.
 * - Deleted children to allow them to fade out on delete.
 * - Temporary flags, for temporary visibility, selectablity etc.
 */
public final class EditorElement implements Parcelable {

  private final UUID        id;
  private final EditorFlags flags;
  private final Matrix      localMatrix  = new Matrix();
  private final Matrix      editorMatrix = new Matrix();

  @Nullable
  private final Renderer renderer;

  private final Matrix temp = new Matrix();

  private final Matrix tempMatrix = new Matrix();

  private final List<EditorElement> children        = new LinkedList<>();
  private final List<EditorElement> deletedChildren = new LinkedList<>();

  @NonNull
  private AnimationMatrix animationMatrix = AnimationMatrix.NULL;

  @NonNull
  private AlphaAnimation alphaAnimation = AlphaAnimation.NULL_1;

  public EditorElement(@Nullable Renderer renderer) {
    this.id       = UUID.randomUUID();
    this.flags    = new EditorFlags();
    this.renderer = renderer;
  }

  private EditorElement(Parcel in) {
    id       = ParcelUtils.readUUID(in);
    flags    = new EditorFlags(in.readInt());
    ParcelUtils.readMatrix(localMatrix, in);
    renderer = in.readParcelable(Renderer.class.getClassLoader());
    in.readTypedList(children, EditorElement.CREATOR);
  }

  UUID getId() {
    return id;
  }

  public @Nullable Renderer getRenderer() {
    return renderer;
  }

  /**
   * Iff Visible,
   * Renders tree with the following localMatrix:
   * <p>
   * viewModelMatrix * localMatrix * editorMatrix * animationMatrix
   * <p>
   * Child nodes are supplied with a viewModelMatrix' = viewModelMatrix * localMatrix * editorMatrix * animationMatrix
   *
   * @param rendererContext Canvas to draw on to.
   */
  void draw(@NonNull RendererContext rendererContext) {
    if (!flags.isVisible() && !flags.isChildrenVisible()) return;

    rendererContext.save();

    rendererContext.canvasMatrix.concat(localMatrix);

    if (rendererContext.isEditing()) {
      rendererContext.canvasMatrix.concat(editorMatrix);
      animationMatrix.preConcatValueTo(rendererContext.canvasMatrix);
    }

    if (flags.isVisible()) {
      float alpha = alphaAnimation.getValue();
      if (alpha > 0) {
        rendererContext.setFade(alpha);
        drawSelf(rendererContext);
        rendererContext.setFade(1f);
      }
    }

    if (flags.isChildrenVisible()) {
      drawChildren(children, rendererContext);
      drawChildren(deletedChildren, rendererContext);
    }

    rendererContext.restore();
  }

  private void drawSelf(@NonNull RendererContext rendererContext) {
    if (renderer == null) return;
    renderer.render(rendererContext);
  }

  private static void drawChildren(@NonNull List<EditorElement> children, @NonNull RendererContext rendererContext) {
    for (EditorElement element : children) {
      element.draw(rendererContext);
    }
  }

  public void addElement(@NonNull EditorElement element) {
    children.add(element);
  }

  public Matrix getLocalMatrix() {
    return localMatrix;
  }

  public Matrix getEditorMatrix() {
    return editorMatrix;
  }

  EditorElement findElement(@NonNull EditorElement toFind, @NonNull Matrix viewMatrix, @NonNull Matrix outInverseModelMatrix) {
    return findElement(viewMatrix, outInverseModelMatrix, (element, inverseMatrix) -> toFind == element);
  }

  EditorElement findElementAt(float x, float y, @NonNull Matrix viewModelMatrix, @NonNull Matrix outInverseModelMatrix) {
    final float[] dst = new float[2];
    final float[] src = { x, y };

    return findElement(viewModelMatrix, outInverseModelMatrix, (element, inverseMatrix) -> {
      Renderer renderer = element.renderer;
      if (renderer == null) return false;
      inverseMatrix.mapPoints(dst, src);
      return element.flags.isSelectable() && renderer.hitTest(dst[0], dst[1]);
    });
  }

  public EditorElement findElement(@NonNull Matrix viewModelMatrix, @NonNull Matrix outInverseModelMatrix, @NonNull FindElementPredicate predicate) {
    temp.set(viewModelMatrix);

    temp.preConcat(localMatrix);
    temp.preConcat(editorMatrix);

    if (temp.invert(tempMatrix)) {

      for (int i = children.size() - 1; i >= 0; i--) {
        EditorElement elementAt = children.get(i).findElement(temp, outInverseModelMatrix, predicate);
        if (elementAt != null) {
          return elementAt;
        }
      }

      if (predicate.test(this, tempMatrix)) {
        outInverseModelMatrix.set(tempMatrix);
        return this;
      }
    }

    return null;
  }

  public EditorFlags getFlags() {
    return flags;
  }

  int getChildCount() {
    return children.size();
  }

  EditorElement getChild(int i) {
    return children.get(i);
  }

  void forAllInTree(@NonNull PerElementFunction function) {
    function.apply(this);
    for (EditorElement child : children) {
      child.forAllInTree(function);
    }
  }

  void deleteChild(@NonNull EditorElement editorElement, @Nullable Runnable invalidate) {
    Iterator<EditorElement> iterator = children.iterator();
    while (iterator.hasNext()) {
      if (iterator.next() == editorElement) {
        iterator.remove();
        addDeletedChildFadingOut(editorElement, invalidate);
      }
    }
  }

  void addDeletedChildFadingOut(@NonNull EditorElement fromElement, @Nullable Runnable invalidate) {
    deletedChildren.add(fromElement);
    fromElement.animateFadeOut(invalidate);
  }

  private void animateFadeOut(@Nullable Runnable invalidate) {
    alphaAnimation = AlphaAnimation.animate(1, 0, invalidate);
  }

  void animateFadeIn(@Nullable Runnable invalidate) {
    alphaAnimation = AlphaAnimation.animate(0, 1, invalidate);
  }

  @Nullable EditorElement parentOf(@NonNull EditorElement element) {
    if (children.contains(element)) {
      return this;
    }
    for (EditorElement child : children) {
      EditorElement parent = child.parentOf(element);
      if (parent != null) {
        return parent;
      }
    }
    return null;
  }

  public void singleScalePulse(@Nullable Runnable invalidate) {
    Matrix scale = new Matrix();
    scale.setScale(1.2f, 1.2f);

    animationMatrix = AnimationMatrix.singlePulse(scale, invalidate);
  }

  public interface PerElementFunction {
    void apply(EditorElement element);
  }

  public interface FindElementPredicate {
    boolean test(EditorElement element, Matrix inverseMatrix);
  }

  public void commitEditorMatrix() {
    if (flags.isEditable()) {
      localMatrix.preConcat(editorMatrix);
      editorMatrix.reset();
    } else {
      rollbackEditorMatrix(null);
    }
  }

  void rollbackEditorMatrix(@Nullable Runnable invalidate) {
    animateEditorTo(new Matrix(), invalidate);
  }

  void buildMap(Map<UUID, EditorElement> map) {
    map.put(id, this);
    for (EditorElement child : children) {
      child.buildMap(map);
    }
  }

  void animateFrom(@NonNull Matrix oldMatrix, @Nullable Runnable invalidate) {
    Matrix oldMatrixCopy = new Matrix(oldMatrix);
    animationMatrix.stop();
    animationMatrix.preConcatValueTo(oldMatrixCopy);
    animationMatrix = AnimationMatrix.animate(oldMatrixCopy, localMatrix, invalidate);
  }

  void animateEditorTo(@NonNull Matrix newEditorMatrix, @Nullable Runnable invalidate) {
    setMatrixWithAnimation(editorMatrix, newEditorMatrix, invalidate);
  }

  void animateLocalTo(@NonNull Matrix newLocalMatrix, @Nullable Runnable invalidate) {
    setMatrixWithAnimation(localMatrix, newLocalMatrix, invalidate);
  }

  /**
   * @param destination Matrix to change
   * @param source      Matrix value to set
   * @param invalidate  Callback to allow animation
   */
  private void setMatrixWithAnimation(@NonNull Matrix destination, @NonNull Matrix source, @Nullable Runnable invalidate) {
    Matrix old = new Matrix(destination);
    animationMatrix.stop();
    animationMatrix.preConcatValueTo(old);
    destination.set(source);
    animationMatrix = AnimationMatrix.animate(old, destination, invalidate);
  }

  Matrix getLocalMatrixAnimating() {
    Matrix matrix = new Matrix(localMatrix);
    animationMatrix.preConcatValueTo(matrix);
    return matrix;
  }

  void stopAnimation() {
    animationMatrix.stop();
  }

  public static final Creator<EditorElement> CREATOR = new Creator<EditorElement>() {
    @Override
    public EditorElement createFromParcel(Parcel in) {
      return new EditorElement(in);
    }

    @Override
    public EditorElement[] newArray(int size) {
      return new EditorElement[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    ParcelUtils.writeUUID(dest, id);
    dest.writeInt(this.flags.asInt());
    ParcelUtils.writeMatrix(dest, localMatrix);
    dest.writeParcelable(renderer, flags);
    dest.writeTypedList(children);
  }
}
