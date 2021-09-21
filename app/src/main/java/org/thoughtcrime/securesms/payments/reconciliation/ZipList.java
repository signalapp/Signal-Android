package org.thoughtcrime.securesms.payments.reconciliation;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ZipList {

  /**
   * Builds a list that contains all items from {@param a} and {@param b}.
   * <p>
   * The items in the resulting list will keep their position relative to other items in their source list.
   * <p>
   * The {@param comparator} is used to decide how to interleave the items in the result by considering the heads of the lists.
   */
  static @NonNull <T> List<T> zipList(@NonNull List<? extends T> a, @NonNull List<? extends T> b, @NonNull Comparator<T> comparator) {
    ArrayList<T> result = new ArrayList<>(a.size() + b.size());

    if (a.isEmpty()) {
      return new ArrayList<>(b);
    }

    if (b.isEmpty()) {
      return new ArrayList<>(a);
    }

    int bIndex = 0;
    int aIndex = 0;

    do {
      T itemA = a.get(aIndex);
      T itemB = b.get(bIndex);
      if (comparator.compare(itemA, itemB) > 0) {
        result.add(itemB);
        bIndex++;
      } else {
        result.add(itemA);
        aIndex++;
      }
    } while (aIndex < a.size() && bIndex < b.size());

    for (int i = aIndex; i < a.size(); i++) {
      result.add(a.get(i));
    }
    for (int i = bIndex; i < b.size(); i++) {
      result.add(b.get(i));
    }

    return result;
  }
}
