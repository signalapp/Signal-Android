package org.thoughtcrime.securesms.conversation.mutiselect

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

  fun isExpired(): Boolean = toSet().any(MultiselectPart::isExpired)

  fun isTextSelected(selectedParts: Set<MultiselectPart>): Boolean {
    val textParts: Set<MultiselectPart> = toSet().filter(this::couldContainText).toSet()

    return textParts.any { selectedParts.contains(it) }
  }

  fun isMediaSelected(selectedParts: Set<MultiselectPart>): Boolean {
    val mediaParts: Set<MultiselectPart> = toSet().filter(this::couldContainMedia).toSet()

    return mediaParts.any { selectedParts.contains(it) }
  }

  private fun couldContainText(multiselectPart: MultiselectPart): Boolean {
    return multiselectPart is MultiselectPart.Text || multiselectPart is MultiselectPart.Message
  }

  private fun couldContainMedia(multiselectPart: MultiselectPart): Boolean {
    return multiselectPart is MultiselectPart.Attachments || multiselectPart is MultiselectPart.Message
  }

  abstract val size: Int

  abstract fun toSet(): Set<MultiselectPart>

  open fun isSingle(): Boolean = false

  open fun asSingle(): Single = throw UnsupportedOperationException()

  open fun asDouble(): Double = throw UnsupportedOperationException()
}
