package org.thoughtcrime.securesms.imageeditor.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

final class UndoRedoStacks implements Parcelable {

  private final ElementStack undoStack;
  private final ElementStack redoStack;

  @NonNull
  private byte[] unchangedState;

  UndoRedoStacks(int limit) {
    this(new ElementStack(limit), new ElementStack(limit), null);
  }

  private UndoRedoStacks(ElementStack undoStack, ElementStack redoStack, @Nullable byte[] unchangedState) {
    this.undoStack = undoStack;
    this.redoStack = redoStack;
    this.unchangedState = unchangedState != null ? unchangedState : new byte[0];
  }

  public static final Creator<UndoRedoStacks> CREATOR = new Creator<UndoRedoStacks>() {
    @Override
    public UndoRedoStacks createFromParcel(Parcel in) {
      return new UndoRedoStacks(
      in.readParcelable(ElementStack.class.getClassLoader()),
      in.readParcelable(ElementStack.class.getClassLoader()),
      in.createByteArray()
      );
    }

    @Override
    public UndoRedoStacks[] newArray(int size) {
      return new UndoRedoStacks[size];
    }
  };

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(undoStack, flags);
    dest.writeParcelable(redoStack, flags);
    dest.writeByteArray(unchangedState);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  ElementStack getUndoStack() {
    return undoStack;
  }

  ElementStack getRedoStack() {
    return redoStack;
  }

  void pushState(@NonNull EditorElement element) {
    if (undoStack.tryPush(element)) {
      redoStack.clear();
    }
  }

  void clear(@NonNull EditorElement element) {
    undoStack.clear();
    redoStack.clear();
    unchangedState = ElementStack.getBytes(element);
  }

  boolean isChanged(@NonNull EditorElement element) {
    return !Arrays.equals(ElementStack.getBytes(element), unchangedState);
  }

  /**
   * As long as there is something different in the stack somewhere, then we can undo.
   */
  boolean canUndo(@NonNull EditorElement currentState) {
    return undoStack.stackContainsStateDifferentFrom(currentState);
  }

  /**
   * As long as there is something different in the stack somewhere, then we can redo.
   */
  boolean canRedo(@NonNull EditorElement currentState) {
    return redoStack.stackContainsStateDifferentFrom(currentState);
  }
}
