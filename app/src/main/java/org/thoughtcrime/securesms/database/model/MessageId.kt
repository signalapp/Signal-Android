package org.thoughtcrime.securesms.database.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the primary key in a [MessageId].
 */
@Parcelize
data class MessageId(
  val id: Long
) : Parcelable {
  fun serialize(): String {
    return "$id|true"
  }

  companion object {
    /**
     * Returns null for invalid IDs. Useful when pulling a possibly-unset ID from a database, or something like that.
     */
    @JvmStatic
    fun fromNullable(id: Long): MessageId? {
      return if (id > 0) {
        MessageId(id)
      } else {
        null
      }
    }

    @JvmStatic
    fun deserialize(serialized: String): MessageId {
      val parts: List<String> = serialized.split("|")
      return MessageId(parts[0].toLong())
    }
  }
}
