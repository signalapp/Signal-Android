package org.thoughtcrime.securesms.badges

import android.content.Context
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.Badge.Category.Companion.fromCode
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.util.ScreenDensity
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.concurrent.TimeUnit

object Badges {

  private val TAG: String = Log.tag(Badges::class.java)

  fun DSLConfiguration.displayBadges(
    context: Context,
    badges: List<Badge>,
    selectedBadge: Badge? = null,
    fadedBadgeId: String? = null
  ) {
    badges
      .map {
        Badge.Model(
          badge = it,
          isSelected = it == selectedBadge,
          isFaded = it.id == fadedBadgeId
        )
      }
      .forEach { customPref(it) }

    val badgeSize = DimensionUnit.DP.toPixels(88f)
    val windowWidth = context.resources.displayMetrics.widthPixels
    val perRow = (windowWidth / badgeSize).toInt()

    val empties = ((perRow - (badges.size % perRow)) % perRow)
    repeat(empties) {
      customPref(Badge.EmptyModel())
    }
  }

  fun createLayoutManagerForGridWithBadges(context: Context): RecyclerView.LayoutManager {
    val layoutManager = FlexboxLayoutManager(context)

    layoutManager.flexDirection = FlexDirection.ROW
    layoutManager.alignItems = AlignItems.CENTER
    layoutManager.justifyContent = JustifyContent.CENTER

    return layoutManager
  }

  private fun getBadgeImageUri(densityPath: String): Uri {
    return Uri.parse(BuildConfig.BADGE_STATIC_ROOT).buildUpon()
      .appendPath(densityPath)
      .build()
  }

  private fun getBestBadgeImageUriForDevice(serviceBadge: SignalServiceProfile.Badge): Pair<Uri, String> {
    return when (ScreenDensity.getBestDensityBucketForDevice()) {
      "ldpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[0]), "ldpi")
      "mdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[1]), "mdpi")
      "hdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[2]), "hdpi")
      "xxhdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[4]), "xxhdpi")
      "xxxhdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[5]), "xxxhdpi")
      else -> Pair(getBadgeImageUri(serviceBadge.sprites6[3]), "xhdpi")
    }.also {
      Log.d(TAG, "Selected badge density ${it.second}")
    }
  }

  private fun getTimestamp(bigDecimal: BigDecimal): Long {
    return Timestamp(bigDecimal.toLong() * 1000).time
  }

  @JvmStatic
  fun fromDatabaseBadge(badge: BadgeList.Badge): Badge {
    return Badge(
      badge.id,
      fromCode(badge.category),
      badge.name,
      badge.description,
      Uri.parse(badge.imageUrl),
      badge.imageDensity,
      badge.expiration,
      badge.visible,
      0L
    )
  }

  @JvmStatic
  fun toDatabaseBadge(badge: Badge): BadgeList.Badge {
    return BadgeList.Badge(
      id = badge.id,
      category = badge.category.code,
      description = badge.description,
      expiration = badge.expirationTimestamp,
      visible = badge.visible,
      name = badge.name,
      imageUrl = badge.imageUrl.toString(),
      imageDensity = badge.imageDensity
    )
  }

  @JvmStatic
  fun fromServiceBadge(serviceBadge: SignalServiceProfile.Badge): Badge {
    val uriAndDensity: Pair<Uri, String> = getBestBadgeImageUriForDevice(serviceBadge)
    return Badge(
      serviceBadge.id,
      fromCode(serviceBadge.category),
      serviceBadge.name,
      serviceBadge.description,
      uriAndDensity.first,
      uriAndDensity.second,
      serviceBadge.expiration?.let { getTimestamp(it) } ?: 0,
      serviceBadge.isVisible,
      TimeUnit.SECONDS.toMillis(serviceBadge.duration)
    )
  }
}
