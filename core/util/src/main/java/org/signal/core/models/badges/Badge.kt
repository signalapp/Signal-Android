/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.badges

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Stable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.models.UriSerializer

/**
 * A Badge that can be collected and displayed by a user.
 */
@Stable
@Parcelize
@Serializable
data class Badge(
  val id: String,
  val category: Category,
  val name: String,
  val description: String,
  @Serializable(with = UriSerializer::class) val imageUrl: Uri,
  val imageDensity: String,
  val expirationTimestamp: Long,
  val visible: Boolean,
  val duration: Long
) : Parcelable {

  fun isExpired(): Boolean = expirationTimestamp < System.currentTimeMillis() && expirationTimestamp > 0
  fun isBoost(): Boolean = id == BOOST_BADGE_ID
  fun isGift(): Boolean = id == GIFT_BADGE_ID
  fun isSubscription(): Boolean = !isBoost() && !isGift()

  fun resolveDescription(shortName: String): String {
    return description.replace("{short_name}", shortName)
  }

  enum class Category(val code: String) {
    Donor("donor"),
    Other("other"),
    Testing("testing"); // Will be removed before final release

    companion object {
      fun fromCode(code: String): Category {
        return when (code) {
          "donor" -> Donor
          "testing" -> Testing
          else -> Other
        }
      }
    }
  }

  companion object {
    const val BOOST_BADGE_ID = "BOOST"
    const val GIFT_BADGE_ID = "GIFT"
  }
}
