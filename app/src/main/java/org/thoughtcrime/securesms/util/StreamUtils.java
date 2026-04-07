/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util;

import com.annimon.stream.Stream;

import java.util.Collection;

public final class StreamUtils {
public static <E> Stream<E> StreamOfCollection(Collection<E> x) {
  return Stream.of(x);
}
  public static <E> Stream<E> StreamOfArray(E[] x) {
    return Stream.of(x);
  }
}
