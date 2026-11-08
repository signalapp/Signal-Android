package org.thoughtcrime.securesms.avatar

import android.net.Uri
import android.os.Bundle
import org.signal.core.util.getParcelableCompat

/**
 * Utility class which encapsulates reading and writing Avatar objects to and from Bundles.
 */
object AvatarBundler {

  private const val URI = "org.thoughtcrime.securesms.avatar.URI"
  private const val DATABASE_ID = "org.thoughtcrime.securesms.avatar.DATABASE_ID"
  private const val SIZE = "org.thoughtcrime.securesms.avatar.SIZE"

  fun bundlePhoto(photo: Avatar.Photo): Bundle = Bundle().apply {
    putParcelable(URI, photo.uri)
    putLong(SIZE, photo.size)
    putDatabaseId(DATABASE_ID, photo.databaseId)
  }

  fun extractPhoto(bundle: Bundle): Avatar.Photo = Avatar.Photo(
    uri = requireNotNull(bundle.getParcelableCompat(URI, Uri::class.java)),
    size = bundle.getLong(SIZE),
    databaseId = bundle.getDatabaseId()
  )

  private fun Bundle.getDatabaseId(): Avatar.DatabaseId {
    val id = getLong(DATABASE_ID, -1L)

    return if (id == -1L) {
      Avatar.DatabaseId.NotSet
    } else {
      Avatar.DatabaseId.Saved(id)
    }
  }

  private fun Bundle.putDatabaseId(key: String, databaseId: Avatar.DatabaseId) {
    if (databaseId is Avatar.DatabaseId.Saved) {
      putLong(key, databaseId.id)
    }
  }
}
