package org.signal.core.util

import java.util.Collections

/**
 * Flattens a List of Map<K, V> into a Map<K, V> using the + operator.
 *
 * @return A Map containing all of the K, V pairings of the maps contained in the original list.
 */
fun <K, V> List<Map<K, V>>.flatten(): Map<K, V> = foldRight(emptyMap()) { a, b -> a + b }

/**
 * Swaps the elements at the specified positions and returns the result in a new immutable list.
 *
 * @param i the index of one element to be swapped.
 * @param j the index of the other element to be swapped.
 *
 * @throws IndexOutOfBoundsException if either i or j is out of range.
 */
fun <E> List<E>.swap(i: Int, j: Int): List<E> {
  val mutableCopy = this.toMutableList()
  Collections.swap(mutableCopy, i, j)
  return mutableCopy.toList()
}

/**
 * Returns the item wrapped in a list, or an empty list of the item is null.
 */
fun <E> E?.asList(): List<E> {
  return if (this == null) emptyList() else listOf(this)
}
