package org.thoughtcrime.securesms.imageeditor.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.Stack;

/**
 * Contains a stack of elements for undo and redo stacks.
 * <p>
 * Elements are mutable, so this stack serializes the element and keeps a stack of serialized data.
 * <p>
 * The stack has a {@link #limit} and if it exceeds that limit the {@link #overflowed} flag is set.
 * So that when used as an undo stack, {@link #isEmpty()} and {@link #isOverflowed()} tell you if the image has ever changed.
 */
final class ElementStack implements Parcelable {

  private final int           limit;
  private final Stack<byte[]> stack = new Stack<>();
  private       boolean       overflowed;

  ElementStack(int limit) {
    this.limit = limit;
  }

  private ElementStack(@NonNull Parcel in) {
    this(in.readInt());
    overflowed = in.readInt() != 0;
    final int count = in.readInt();
    for (int i = 0; i < count; i++) {
      stack.add(i, in.createByteArray());
    }
  }

  /**
   * Pushes an element to the stack iff the element's serialized value is different to any found at
   * the top of the stack.
   *
   * @param element new editor element state.
   * @return true iff the pushed item was different to the top item.
   */
  boolean tryPush(@NonNull EditorElement element) {
    Parcel parcel = Parcel.obtain();
    byte[] bytes;
    try {
      parcel.writeParcelable(element, 0);
      bytes = parcel.marshall();
    } finally {
      parcel.recycle();
    }
    boolean push = stack.isEmpty() || !Arrays.equals(bytes, stack.peek());
    if (push) {
      stack.push(bytes);
      if (stack.size() > limit) {
        stack.remove(0);
        overflowed = true;
      }
    }
    return push;
  }

  @Nullable EditorElement pop() {
    if (stack.empty()) return null;

    byte[] data = stack.pop();
    Parcel parcel = Parcel.obtain();
    try {
      parcel.unmarshall(data, 0, data.length);
      parcel.setDataPosition(0);
      return parcel.readParcelable(EditorElement.class.getClassLoader());
    } finally {
      parcel.recycle();
    }
  }

  void clear() {
    stack.clear();
  }

  public static final Creator<ElementStack> CREATOR = new Creator<ElementStack>() {
    @Override
    public ElementStack createFromParcel(Parcel in) {
      return new ElementStack(in);
    }

    @Override
    public ElementStack[] newArray(int size) {
      return new ElementStack[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(limit);
    dest.writeInt(overflowed ? 1 : 0);
    final int count = stack.size();
    dest.writeInt(count);
    for (int i = 0; i < count; i++) {
      dest.writeByteArray(stack.get(i));
    }
  }

  boolean isEmpty() {
    return stack.isEmpty();
  }

  boolean isOverflowed() {
    return overflowed;
  }
}
