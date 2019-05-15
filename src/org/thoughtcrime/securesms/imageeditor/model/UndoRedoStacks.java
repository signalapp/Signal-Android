package org.thoughtcrime.securesms.imageeditor.model;

import android.os.Parcel;
import android.os.Parcelable;

final class UndoRedoStacks implements Parcelable {

  private final ElementStack undoStack;
  private final ElementStack redoStack;

  public UndoRedoStacks(int limit) {
    this(new ElementStack(limit), new ElementStack(limit));
  }

  private UndoRedoStacks(ElementStack undoStack, ElementStack redoStack) {
    this.undoStack = undoStack;
    this.redoStack = redoStack;
  }

  public static final Creator<UndoRedoStacks> CREATOR = new Creator<UndoRedoStacks>() {
    @Override
    public UndoRedoStacks createFromParcel(Parcel in) {
      return new UndoRedoStacks(
      in.readParcelable(ElementStack.class.getClassLoader()),
      in.readParcelable(ElementStack.class.getClassLoader())
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
}
