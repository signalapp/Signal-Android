/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Treats the string as a serialized list of tokens and tells you if an item is present in the list.
 * In addition to exact matches, this handles wildcards at the end of an item.
 *
 * e.g. a,b,c*,d
 */
fun String.asListContains(item: String): Boolean {
  val items: List<String> = this
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toList()

  val exactMatches = items.filter { it.last() != '*' }
  val prefixMatches = items.filter { it.last() == '*' }

  return exactMatches.contains(item) ||
    prefixMatches
      .map { it.substring(0, it.length - 1) }
      .any { item.startsWith(it) }
}

fun String?.emptyIfNull(): String {
  return this ?: ""
}

/**
 * Turns a multi-line string into a single-line string stripped of indentation, separated by spaces instead of newlines.
 *
 * e.g.
 *
 * a
 *   b
 * c
 *
 * turns into
 *
 * a b c
 */
fun String.toSingleLine(): String {
  return this.trimIndent().split("\n").joinToString(separator = " ")
}

fun String?.nullIfEmpty(): String? {
  return this?.ifEmpty {
    null
  }
}

fun String?.nullIfBlank(): String? {
  return this?.ifBlank {
    null
  }
}

@OptIn(ExperimentalContracts::class)
fun CharSequence?.isNotNullOrBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullOrBlank != null)
  }
  return !this.isNullOrBlank()
}
