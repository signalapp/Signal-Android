package org.session.libsession.snode.utilities

import java.security.SecureRandom

/**
 * Uses `SecureRandom` to pick an element from this collection.
 */
fun <T> Collection<T>.getRandomElementOrNull(): T? {
    if (isEmpty()) return null
    val index = SecureRandom().nextInt(size) // SecureRandom() should be cryptographically secure
    return elementAtOrNull(index)
}

/**
 * Uses `SecureRandom` to pick an element from this collection.
 */
fun <T> Collection<T>.getRandomElement(): T {
    return getRandomElementOrNull()!!
}
