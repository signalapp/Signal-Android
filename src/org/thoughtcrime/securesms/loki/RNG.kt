package org.thoughtcrime.securesms.loki

class RNG(hash: Long) {
  private var seed: Long
  private val initial: Long

  private val maxInt32 = Int.MAX_VALUE.toLong()

  init {
    seed = hash % maxInt32
    if (seed <= 0) {
      seed = maxInt32 - 1
    }
    initial = seed
  }

  fun next(): Long {
    val newSeed = (seed * 16807) % maxInt32
    seed = newSeed
    return seed
  }

  fun nextFloat(): Float {
    return (next() - 1).toFloat() / (maxInt32 - 1)
  }

  fun reset() {
    seed = initial
  }
}