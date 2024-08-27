package org.thoughtcrime.securesms.linkdevice

/**
 * Class that represents a linked device
 */
data class Device(val id: Long, val name: String?, val createdMillis: Long, val lastSeenMillis: Long)
