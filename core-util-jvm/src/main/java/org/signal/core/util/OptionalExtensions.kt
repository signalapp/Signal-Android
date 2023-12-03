/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.util.Optional

fun <E> Optional<E>.or(other: Optional<E>): Optional<E> {
  return if (this.isPresent) {
    this
  } else {
    other
  }
}

fun <E> Optional<E>.isAbsent(): Boolean {
  return !isPresent
}

fun <E : Any> E?.toOptional(): Optional<E> {
  return Optional.ofNullable(this)
}

fun <E> Optional<E>.orNull(): E? {
  return orElse(null)
}
