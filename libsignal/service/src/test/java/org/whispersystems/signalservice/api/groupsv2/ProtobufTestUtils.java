package org.whispersystems.signalservice.api.groupsv2;

import com.squareup.wire.Message;
import com.squareup.wire.WireField;

import java.util.stream.Stream;

final class ProtobufTestUtils {

  /**
   * Finds the largest declared field number in the supplied protobuf class.
   */
  static int getMaxDeclaredFieldNumber(Class<? extends Message<?, ?>> protobufClass) {
    return Stream.of(protobufClass.getFields())
                 .map(f -> f.getAnnotationsByType(WireField.class))
                 .filter(a -> a.length == 1)
                 .map(a -> a[0].tag())
                 .max(Integer::compareTo)
                 .orElse(0);
  }
}
