package org.thoughtcrime.securesms.database.model

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents the primary key in a [MessageId].
 */
@Serializable
@Parcelize
data class MessageId(
  val id: Long
) : Parcelable {
  fun serialize(): String {
    return "$id|true"
  }

  class NavType : androidx.navigation.NavType<MessageId>(false) {
    override fun get(bundle: Bundle, key: String): MessageId? = bundle.getLong(key, -1).takeIf { it >= 0 }?.let { MessageId(it) }
    override fun parseValue(value: String): MessageId = MessageId(value.toLong())
    override fun put(bundle: Bundle, key: String, value: MessageId) = bundle.putLong(key, value.id)
    override fun serializeAsValue(value: MessageId): String = value.id.toString()
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
