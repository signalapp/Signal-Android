package org.signal.core.util

import java.util.Optional

fun <E> Optional<E>.or(other: Optional<E>): Optional<E> {
  return if (this.isPresent) {
    this
  } else {
    other
  }
}