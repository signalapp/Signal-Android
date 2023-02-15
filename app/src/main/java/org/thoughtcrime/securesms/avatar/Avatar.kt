package org.thoughtcrime.securesms.avatar

import android.net.Uri
import org.thoughtcrime.securesms.R

/**
 * Represents an Avatar which the user can choose, edit, and render into a bitmap via the renderer.
 */
sealed class Avatar(
  open val databaseId: DatabaseId
) {
  data class Resource(
    val resourceId: Int,
    val color: Avatars.ColorPair
  ) : Avatar(DatabaseId.DoNotPersist) {
    override fun isSameAs(other: Avatar): Boolean {
      return other is Resource && other.resourceId == resourceId
    }
  }

  data class Text(
    val text: String,
    val color: Avatars.ColorPair,
    override val databaseId: DatabaseId
  ) : Avatar(databaseId) {
    override fun withDatabaseId(databaseId: DatabaseId): Avatar {
      return copy(databaseId = databaseId)
    }

    override fun isSameAs(other: Avatar): Boolean {
      return other is Text && other.databaseId == databaseId
    }
  }

  data class Vector(
    val key: String,
    val color: Avatars.ColorPair,
    override val databaseId: DatabaseId
  ) : Avatar(databaseId) {
    override fun withDatabaseId(databaseId: DatabaseId): Avatar {
      return copy(databaseId = databaseId)
    }

    override fun isSameAs(other: Avatar): Boolean {
      return other is Vector && other.key == key
    }
  }

  data class Photo(
    val uri: Uri,
    val size: Long,
    override val databaseId: DatabaseId
  ) : Avatar(databaseId) {
    override fun withDatabaseId(databaseId: DatabaseId): Avatar {
      return copy(databaseId = databaseId)
    }

    override fun isSameAs(other: Avatar): Boolean {
      return other is Photo && databaseId == other.databaseId
    }
  }

  open fun withDatabaseId(databaseId: DatabaseId): Avatar {
    throw UnsupportedOperationException()
  }

  abstract fun isSameAs(other: Avatar): Boolean

  companion object {
    fun getDefaultForSelf(): Resource = Resource(R.drawable.ic_profile_outline_40, Avatars.colors.random())
    fun getDefaultForGroup(): Resource = Resource(R.drawable.ic_group_outline_40, Avatars.colors.random())
  }

  sealed class DatabaseId {
    object DoNotPersist : DatabaseId()
    object NotSet : DatabaseId()
    data class Saved(val id: Long) : DatabaseId()
  }
}
