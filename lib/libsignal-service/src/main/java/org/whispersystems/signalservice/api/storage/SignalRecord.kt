package org.whispersystems.signalservice.api.storage

import com.squareup.wire.Message
import org.signal.core.util.hasUnknownFields
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Pairs a storage record with its id. Also contains some useful common methods.
 */
sealed interface SignalRecord<E> {
  val id: StorageId
  val proto: E

  val serializedUnknowns: ByteArray?
    get() = (proto as Message<*, *>).takeIf { it.hasUnknownFields() }?.encode()

  fun describeDiff(other: SignalRecord<*>): String {
    if (this::class != other::class) {
      return "Classes are different!"
    }

    if (this.proto!!::class != other.proto!!::class) {
      return "Proto classes are different!"
    }

    val myFields = this.proto!!::class.memberProperties
    val otherFields = other.proto!!::class.memberProperties

    val myFieldsByName = myFields
      .filter { it.isFinal && it.visibility == KVisibility.PUBLIC }
      .associate { it.name to it.getter.call(this.proto!!) }

    val otherFieldsByName = otherFields
      .filter { it.isFinal && it.visibility == KVisibility.PUBLIC }
      .associate { it.name to it.getter.call(other.proto!!) }

    val mismatching = mutableListOf<String>()

    if (this.id != other.id) {
      mismatching += "id"
    }

    for (key in myFieldsByName.keys) {
      val myValue = myFieldsByName[key]
      val otherValue = otherFieldsByName[key]

      if (myValue != otherValue) {
        mismatching += key
      }
    }

    return if (mismatching.isEmpty()) {
      "All fields match."
    } else {
      mismatching.sorted().joinToString(prefix = "Some fields differ: ", separator = ", ")
    }
  }
}
