/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.IOException
import java.io.UnsupportedEncodingException

object Base64 {

  /**
   * Encodes the bytes as a normal Base64 string with padding. Not URL safe. For url-safe, use [encodeUrlSafe].
   *
   * Note: the [offset] and [length] are there to support a legacy usecase, which is why they're not present on
   * the other encode* methods.
   */
  @JvmOverloads
  @JvmStatic
  fun encodeWithPadding(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
    return Base64Tools.encodeBytes(bytes, offset, length)
  }

  /**
   * Encodes the bytes as a normal Base64 string without padding. Not URL safe. For url-safe, use [encodeUrlSafe].
   */
  @JvmStatic
  fun encodeWithoutPadding(bytes: ByteArray): String {
    return Base64Tools.encodeBytes(bytes).stripPadding()
  }

  /**
   * Encodes the bytes as a url-safe Base64 string with padding. It basically replaces the '+' and '/' characters in the
   * normal encoding scheme with '-' and '_'.
   */
  @JvmStatic
  fun encodeUrlSafeWithPadding(bytes: ByteArray): String {
    return Base64Tools.encodeBytes(bytes, Base64Tools.URL_SAFE or Base64Tools.DONT_GUNZIP)
  }

  /**
   * Encodes the bytes as a url-safe Base64 string without padding. It basically replaces the '+' and '/' characters in the
   * normal encoding scheme with '-' and '_'.
   */
  @JvmStatic
  fun encodeUrlSafeWithoutPadding(bytes: ByteArray): String {
    return Base64Tools.encodeBytes(bytes, Base64Tools.URL_SAFE or Base64Tools.DONT_GUNZIP).stripPadding()
  }

  /**
   * A very lenient decoder. Does not care about the presence of padding or whether it's url-safe or not. It'll just decode it.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun decode(value: String): ByteArray {
    return if (value.contains('-') || value.contains('_')) {
      Base64Tools.decode(value.withPaddingIfNeeded(), Base64Tools.URL_SAFE or Base64Tools.DONT_GUNZIP)
    } else {
      Base64Tools.decode(value.withPaddingIfNeeded())
    }
  }

  @JvmStatic
  fun decode(value: ByteArray): ByteArray {
    // This pattern of trying US_ASCII first mimics how Base64Tools handles strings
    return try {
      decode(String(value, Charsets.US_ASCII))
    } catch (e: UnsupportedEncodingException) {
      decode(String(value, Charsets.UTF_8))
    }
  }

  /**
   * The same as [decode], except that instead of requiring you to handle an exception, this will return null
   * if the input is null or cannot be decoded.
   */
  @JvmStatic
  fun decodeOrNull(value: String?): ByteArray? {
    if (value == null) {
      return null
    }

    return try {
      decode(value)
    } catch (e: IOException) {
      null
    }
  }

  /**
   * The same as [decode], except that instead of requiring you to handle an exception, this will just crash on invalid base64 strings.
   * Should only be used if the value is definitely a valid base64 string.
   */
  @JvmStatic
  fun decodeOrThrow(value: String): ByteArray {
    return try {
      decode(value)
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  /**
   * The same as [decode], except that instead of requiring you to handle an exception, this will just crash on invalid base64 strings.
   * It also allows null inputs. If the input is null, the outpul will be null.
   * Should only be used if the value is definitely a valid base64 string.
   */
  @JvmStatic
  fun decodeNullableOrThrow(value: String?): ByteArray? {
    if (value == null) {
      return null
    }

    return try {
      decode(value)
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  private fun String.withPaddingIfNeeded(): String {
    return when (this.length % 4) {
      2 -> "$this=="
      3 -> "$this="
      else -> this
    }
  }

  private fun String.stripPadding(): String {
    return this.replace("=", "")
  }
}
