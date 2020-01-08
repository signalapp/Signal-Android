package org.thoughtcrime.securesms.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SetUtil {
  private SetUtil() {}

  public static <E> Set<E> intersection(Collection<E> a, Collection<E> b) {
    Set<E> intersection = new LinkedHashSet<>(a);
    intersection.retainAll(b);
    return intersection;
  }

  public static <E> Set<E> difference(Collection<E> a, Collection<E> b) {
    Set<E> difference = new LinkedHashSet<>(a);
    difference.removeAll(b);
    return difference;
  }

  public static <E> Set<E> union(Set<E>... sets) {
    Set<E> result = new LinkedHashSet<>();

    for (Set<E> set : sets) {
      result.addAll(set);
    }

    return result;
  }
}
