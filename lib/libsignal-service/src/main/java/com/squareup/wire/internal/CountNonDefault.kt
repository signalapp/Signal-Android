/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.squareup.wire.internal

import okio.ByteString

/**
 * File inspired by countNonNull implementations in com.squareup.wire.internal.Internal.kt
 *
 * Do not change the name without also updating the name used in wire-handler jar project. Our custom
 * handler tweaks the generated proto code to call this less restrictive oneOf validators. Wire requires
 * at most one non-null but iOS can't handle that currently, so we use at most one non-null and non-default.
 *
 * For example, a oneOf property that is an int but set to 0 is valid.
 */

/** Do not change the name. Returns the number of non-null values in `a, b`. */
fun countNonDefa(a: Any?, b: Any?): Int {
  return a.isNonDefault() + b.isNonDefault()
}

/** Do not change the name. Returns the number of non-null values in `a, b, c`. */
fun countNonDefa(a: Any?, b: Any?, c: Any?): Int {
  return a.isNonDefault() + b.isNonDefault() + c.isNonDefault()
}

/** Do not change the name. Returns the number of non-null values in `a, b, c, d, rest`. */
fun countNonDefa(a: Any?, b: Any?, c: Any?, d: Any?, vararg rest: Any?): Int {
  var result = 0
  result += a.isNonDefault()
  result += b.isNonDefault()
  result += c.isNonDefault()
  result += d.isNonDefault()
  for (o in rest) {
    result += o.isNonDefault()
  }
  return result
}

private fun Any?.isNonDefault(): Int {
  return when {
    this == null -> 0
    this is Boolean && this == false -> 0
    this is ByteString && this.size == 0 -> 0
    this is Byte && this == 0.toByte() -> 0
    this is Short && this == 0.toShort() -> 0
    this is Int && this == 0 -> 0
    this is Long && this == 0L -> 0
    this is String && this == "" -> 0
    this is Double && this == 0.0 -> 0
    this is Float && this == 0f -> 0
    else -> 1
  }
}
