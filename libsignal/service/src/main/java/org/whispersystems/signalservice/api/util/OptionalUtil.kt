package org.whispersystems.signalservice.api.util

import com.google.protobuf.ByteString
import java.util.Optional

object OptionalUtil {
  @JvmStatic
  @SafeVarargs
  fun <E : Any> or(vararg optionals: Optional<E>): Optional<E> {
    return optionals.firstOrNull { it.isPresent } ?: Optional.empty()
  }

  @JvmStatic
  fun byteArrayEquals(a: Optional<ByteArray>, b: Optional<ByteArray>): Boolean {
    return if (a.isPresent != b.isPresent) {
      false
    } else if (a.isPresent) {
      a.get().contentEquals(b.get())
    } else {
      true
    }
  }

  @JvmStatic
  fun byteArrayHashCode(bytes: Optional<ByteArray>): Int {
    return if (bytes.isPresent) {
      bytes.get().contentHashCode()
    } else {
      0
    }
  }

  @JvmStatic
  fun absentIfEmpty(value: String?): Optional<String> {
    return if (value == null || value.isEmpty()) {
      Optional.empty()
    } else {
      Optional.of(value)
    }
  }

  @JvmStatic
  fun absentIfEmpty(value: ByteString?): Optional<ByteArray> {
    return if (value == null || value.isEmpty) {
      Optional.empty()
    } else {
      Optional.of(value.toByteArray())
    }
  }

  @JvmStatic
  fun <E : Any> emptyIfListEmpty(list: List<E>?): Optional<List<E>> {
    return list.asOptional()
  }

  fun <E : Any> E?.asOptional(): Optional<E> {
    return Optional.ofNullable(this)
  }

  fun <E : Any> List<E>?.asOptional(): Optional<List<E>> {
    return Optional.ofNullable(this?.takeIf { it.isNotEmpty() })
  }

  fun String?.emptyIfStringEmpty(): Optional<String> {
    return absentIfEmpty(this)
  }
}
