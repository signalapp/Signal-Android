package org.whispersystems.signalservice.api.groupsv2

import com.squareup.wire.Message
import com.squareup.wire.WireField

internal object ProtobufTestUtils {
  /**
   * Finds the largest declared field number in the supplied protobuf class.
   */
  fun getMaxDeclaredFieldNumber(protobufClass: Class<out Message<*, *>?>): Int {
    return protobufClass.fields
      .map { field -> field.getAnnotationsByType(WireField::class.java) }
      .filter { array -> array.size == 1 }
      .maxOfOrNull { array -> array.first().tag }
      ?: 0
  }
}
