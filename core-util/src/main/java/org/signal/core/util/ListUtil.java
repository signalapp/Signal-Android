package org.signal.core.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class ListUtil {
  private ListUtil() {}

  public static <E> List<List<E>> chunk(@NonNull List<E> list, int chunkSize) {
    List<List<E>> chunks = new ArrayList<>(list.size() / chunkSize);

    for (int i = 0; i < list.size(); i += chunkSize) {
      List<E> chunk = list.subList(i, Math.min(list.size(), i + chunkSize));
      chunks.add(chunk);
    }

    return chunks;
  }

  @SafeVarargs
  public static <T> List<T> concat(Collection<T>... items) {
    final List<T> concat = new ArrayList<>(Stream.of(items).map(Collection::size).reduce(0, Integer::sum));

    for (Collection<T> list : items) {
      concat.addAll(list);
    }

    return concat;
  }

  public static <T> List<T> emptyIfNull(List<T> list) {
    return list == null ? Collections.emptyList() : list;
  }
}
