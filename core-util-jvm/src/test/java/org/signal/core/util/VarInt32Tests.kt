/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class VarInt32Tests {

  /**
   * Tests a random sampling of integers. The faster and more practical version of [testAll].
   */
  @Test
  fun testRandomSampling() {
    val randomInts = (0..100_000).map { Random.nextInt() }

    val bytes = ByteArrayOutputStream().use { outputStream ->
      for (value in randomInts) {
        outputStream.writeVarInt32(value)
      }
      outputStream
    }.toByteArray()

    bytes.inputStream().use { inputStream ->
      for (value in randomInts) {
        val read = inputStream.readVarInt32()
        assertEquals(value, read)
      }
    }
  }

  /**
   * Exhaustively checks reading and writing a varint for all possible integers.
   * We can't keep everything in memory, so instead we use sequences to grab a million at a time,
   * then run smaller chunks of those in parallel.
   */
  @Ignore("This test is very slow (over a minute). It was run once to verify correctness, but the random sampling test should be sufficient for catching regressions.")
  @Test
  fun testAll() {
    val counter = AtomicInteger(0)

    (Int.MIN_VALUE..Int.MAX_VALUE)
      .asSequence()
      .chunked(1_000_000)
      .forEach { bigChunk ->
        bigChunk
          .chunked(100_000)
          .parallelStream()
          .forEach { smallChunk ->
            println("Chunk ${counter.addAndGet(1)}")

            val bytes = ByteArrayOutputStream().use { outputStream ->
              for (value in smallChunk) {
                outputStream.writeVarInt32(value)
              }
              outputStream
            }.toByteArray()

            bytes.inputStream().use { inputStream ->
              for (value in smallChunk) {
                val read = inputStream.readVarInt32()
                assertEquals(value, read)
              }
            }
          }
      }
  }
}
