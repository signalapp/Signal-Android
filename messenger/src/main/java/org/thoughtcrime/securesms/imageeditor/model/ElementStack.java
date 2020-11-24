package org.thoughtcrime.securesms.imageeditor.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Stack;

/**
 * Contains a stack of elements for undo and redo stacks.
 * <p>
 * Elements are mutable, so this stack serializes the element and keeps a stack of serialized data.
 * <p>
 * The stack has a {@link #limit} and if it exceeds that limit during a push the second to earliest item
 * is removed so that it can always go back to the first state. Effectively collapsing the history for
 * the start of the stack.
 */
final class ElementStack implements Parcelable {

  private final int           limit;
  private final Stack<byte[]> stack = new Stack<>();

  ElementStack(int limit) {
    this.limit = limit;
  }

  private ElementStack(@NonNull Parcel in) {
    this(in.readInt());
    final int count = in.readInt();
    for (int i = 0; i < count; i++) {
      stack.add(i, in.createByteArray());
    }
  }

  /**
   * Pushes an element to the stack iff the element's serialized value is different to any found at
   * the top of the stack.
   * <p>
   * Removes the second to earliest item if it is overflowing.
   *
   * @param element new editor element state.
   * @return true iff the pushed item was different to the top item.
   */
  boolean tryPush(@NonNull EditorElement element) {
    byte[]  bytes = getBytes(element);
    boolean push  = stack.isEmpty() || !Arrays.equals(bytes, stack.peek());

    if (push) {
      stack.push(bytes);
      if (stack.size() > limit) {
        stack.remove(1);
      }
    }
    return push;
  }

  static byte[] getBytes(@NonNull Parcelable parcelable) {
    Parcel parcel = Parcel.obtain();
    byte[] bytes;
    try {
      parcel.writeParcelable(parcelable, 0);
      bytes = parcel.marshall();
    } finally {
      parcel.recycle();
    }
    return bytes;
  }

  /**
   * Pops the first different state from the supplied element.
   */
  @Nullable EditorElement pop(@NonNull EditorElement element) {
    if (stack.empty()) return null;

    byte[] elementBytes = getBytes(element);
    byte[] stackData    = null;

    while (!stack.empty() && stackData == null) {
      byte[] topData = stack.pop();

      if (!Arrays.equals(topData, elementBytes)) {
        stackData = topData;
      }
    }

    if (stackData == null) return null;

    Parcel parcel = Parcel.obtain();
    try {
      parcel.unmarshall(stackData, 0, stackData.length);
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
    final int count = stack.size();
    dest.writeInt(count);
    for (int i = 0; i < count; i++) {
      dest.writeByteArray(stack.get(i));
    }
  }

  boolean stackContainsStateDifferentFrom(@NonNull EditorElement element) {
    if (stack.isEmpty()) return false;

    byte[] currentStateBytes = getBytes(element);

    for (byte[] item : stack) {
      if (!Arrays.equals(item, currentStateBytes)) {
        return true;
      }
    }

    return false;
  }
}
