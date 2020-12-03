package org.signal.paging;

import androidx.annotation.NonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * A placeholder class for efficiently storing lists that are mostly empty space.
 * TODO [greyson][paging]
 */
public class CompressedList<E> extends AbstractList<E> {

  private final List<E> wrapped;

  public CompressedList(@NonNull List<E> source) {
    this.wrapped = new ArrayList<>(source);
  }

  public CompressedList(int totalSize) {
    this.wrapped = new ArrayList<>(totalSize);

    for (int i = 0; i < totalSize; i++) {
      wrapped.add(null);
    }
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  @Override
  public E get(int index) {
    return wrapped.get(index);
  }

  @Override
  public E set(int globalIndex, E element) {
    return wrapped.set(globalIndex, element);
  }
}
