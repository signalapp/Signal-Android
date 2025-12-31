/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import okio.utf8Size
import org.signal.core.util.logging.Log
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val TAG: String = "StringExtensions"

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

/**
 * Encode this string in a url-safe way with UTF-8 encoding.
 */
fun String.urlEncode(): String {
  return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

/**
 * Splits a string into two parts, such that the first part will be at most [byteLength] bytes long.

 * The first item of the pair will be the shortened string, and the second item will be the remainder.
 * Appending the two parts together will give you back the original string.
 *
 * If the input string is already less than [byteLength] bytes, the second item will be null.
 */
fun String.splitByByteLength(byteLength: Int): Pair<String, String?> {
  if (this.utf8Size() <= byteLength) {
    return this to null
  }

  val charBuffer = CharBuffer.wrap(this)
  val encoder = Charsets.UTF_8.newEncoder()
  val outputBuffer = ByteBuffer.allocate(byteLength)

  encoder.encode(charBuffer, outputBuffer, true)
  charBuffer.flip()

  var firstPart = charBuffer.toString()

  // Unfortunately some Android implementations will cause the charBuffer to go a step beyond what it should.
  // It's always extremely close (in testing, only ever off by 1), but as a workaround, we chop off characters
  // at the end until it fits. Bummer.
  while (firstPart.utf8Size() > byteLength) {
    Log.w(TAG, "Had to chop off a character to make it fit under the byte limit.")
    firstPart = firstPart.substring(0, firstPart.length - 1)
  }

  val remainder = this.substring(firstPart.length)
  return firstPart to remainder
}
