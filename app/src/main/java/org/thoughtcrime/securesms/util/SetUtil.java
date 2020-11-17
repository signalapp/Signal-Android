package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;

import com.google.android.collect.Sets;

import java.util.Collection;
import java.util.HashSet;
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

  public static <E> Set<E> union(Set<E> a, Set<E> b) {
    Set<E> result = new LinkedHashSet<>(a);
    result.addAll(b);
    return result;
  }

  @SuppressLint("NewApi")
  public static <E> HashSet<E> newHashSet(E... elements) {
    return Sets.newHashSet(elements);
  }
}
