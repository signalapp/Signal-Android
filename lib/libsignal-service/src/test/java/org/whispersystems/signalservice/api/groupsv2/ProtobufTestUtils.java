package org.whispersystems.signalservice.api.groupsv2;

import com.squareup.wire.Message;
import com.squareup.wire.WireField;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

final class ProtobufTestUtils {

  /** Tags that should be ignored and not count as part of 'needs support' in the various group decryption tests. */
  static final Set<Integer> IGNORED_DECRYPTED_GROUP_TAGS = Collections.singleton(64);

  /**
   * Finds the largest declared field number in the supplied protobuf class.
   */
  static int getMaxDeclaredFieldNumber(Class<? extends Message<?, ?>> protobufClass) {
    return getMaxDeclaredFieldNumber(protobufClass, Collections.emptySet());
  }

  /**
   * Finds the largest declared field number in the supplied protobuf class.
   */
  static int getMaxDeclaredFieldNumber(Class<? extends Message<?, ?>> protobufClass, Set<Integer> excludeTags) {
    return Stream.of(protobufClass.getFields())
                 .map(f -> f.getAnnotationsByType(WireField.class))
                 .filter(a -> a.length == 1)
                 .map(a -> a[0].tag())
                 .filter(t -> !excludeTags.contains(t))
                 .max(Integer::compareTo)
                 .orElse(0);
  }
}
