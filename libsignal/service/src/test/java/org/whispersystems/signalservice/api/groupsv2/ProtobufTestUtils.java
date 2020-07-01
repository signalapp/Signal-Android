package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.MessageLite;

import java.util.stream.Stream;

final class ProtobufTestUtils {

  /**
   * Finds the largest declared field number in the supplied protobuf class.
   */
  static int getMaxDeclaredFieldNumber(Class<? extends MessageLite> protobufClass) {
    return Stream.of(protobufClass.getFields())
                 .filter(f -> f.getType() == int.class)
                 .mapToInt(f -> {
                   try {
                     return (int) f.get(null);
                   } catch (IllegalAccessException e) {
                     throw new AssertionError(e);
                   }
                 })
                 .max()
                 .orElse(0);
  }
}
