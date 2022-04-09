package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.signal.core.util.SetUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Helps determine the difference between two collections based on their {@link #equals(Object)}
 * implementations.
 */
public class DiffHelper {

  /**
   * @return Result indicating the differences between the two collections. Important: The iteration
   *         order of the result will not necessarily match the iteration order of the original
   *         collection.
   */
  public static <E> Result<E> calculate(@NonNull Collection<E> oldList, @NonNull Collection<E> newList) {
    Set<E> inserted = SetUtil.difference(newList, oldList);
    Set<E> removed  = SetUtil.difference(oldList, newList);

    return new Result<>(inserted, removed);
  }

  public static class Result<E> {
    private final Collection<E> inserted;
    private final Collection<E> removed;

    public Result(@NonNull Collection<E> inserted, @NonNull Collection<E> removed) {
      this.removed  = removed;
      this.inserted = inserted;
    }

    public @NonNull Collection<E> getInserted() {
      return inserted;
    }

    public @NonNull Collection<E> getRemoved() {
      return removed;
    }
  }
}
