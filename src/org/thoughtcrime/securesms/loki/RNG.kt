package org.thoughtcrime.securesms.loki

class RNG(hash: Long) {
  private var seed: Long
  private val initial: Long

  init {
    seed = hash % 2147483647
    if (seed <= 0) {
      seed = 2147483646
    }
    initial = seed
  }

  fun next(): Long {
    val newSeed = (seed * 16807) % 2147483647
    seed = newSeed
    return seed
  }

  fun nextFloat(): Float {
    return (next() - 1).toFloat() / 2147483646
  }

  fun reset() {
    seed = initial
  }
}