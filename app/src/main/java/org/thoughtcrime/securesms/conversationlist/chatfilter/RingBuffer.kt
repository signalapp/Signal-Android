package org.thoughtcrime.securesms.conversationlist.chatfilter

import androidx.collection.CircularArray

class RingBuffer<T>(@androidx.annotation.IntRange(from = 0) private val capacity: Int) {
  private val buffer = CircularArray<T>(capacity)

  fun add(t: T) {
    if (size() == capacity) {
      buffer.popFirst()
    }

    buffer.addLast(t)
  }

  fun size() = buffer.size()

  fun clear() = buffer.clear()

  operator fun get(index: Int): T = buffer.get(index)
}
