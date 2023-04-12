package org.signal.core.util

/**
 * Flattens a List of Map<K, V> into a Map<K, V> using the + operator.
 *
 * @return A Map containing all of the K, V pairings of the maps contained in the original list.
 */
fun <K, V> List<Map<K, V>>.flatten(): Map<K, V> = foldRight(emptyMap()) { a, b -> a + b }
