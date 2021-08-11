package org.thoughtcrime.securesms.conversation.mutiselect

import java.lang.IllegalArgumentException
import java.lang.UnsupportedOperationException

sealed class MultiselectCollection {

  data class Single(val singlePart: MultiselectPart) : MultiselectCollection() {

    override val size: Int = 1

    override fun toSet(): Set<MultiselectPart> = setOf(singlePart)

    override fun isSingle(): Boolean = true

    override fun asSingle(): Single = this
  }

  data class Double(val topPart: MultiselectPart, val bottomPart: MultiselectPart) : MultiselectCollection() {

    override val size: Int = 2

    override fun toSet(): Set<MultiselectPart> = linkedSetOf(topPart, bottomPart)

    override fun asDouble(): Double = this
  }

  companion object {
    fun fromSet(partsSet: Set<MultiselectPart>): MultiselectCollection {
      return when (partsSet.size) {
        1 -> Single(partsSet.first())
        2 -> {
          val iter = partsSet.iterator()
          Double(iter.next(), iter.next())
        }
        else -> throw IllegalArgumentException("Unsupported set size: ${partsSet.size}")
      }
    }
  }

  abstract val size: Int

  abstract fun toSet(): Set<MultiselectPart>

  open fun isSingle(): Boolean = false

  open fun asSingle(): Single = throw UnsupportedOperationException()

  open fun asDouble(): Double = throw UnsupportedOperationException()
}
