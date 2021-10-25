package org.thoughtcrime.securesms.badges

import android.content.Context
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.Badge.Category.Companion.fromCode
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.util.ScreenDensity
import org.whispersystems.libsignal.util.Pair
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import java.math.BigDecimal
import java.sql.Timestamp

object Badges {
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

    val perRow = context.resources.getInteger(R.integer.badge_columns)
    val empties = (perRow - (badges.size % perRow)) % perRow
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
    val bestDensity = ScreenDensity.getBestDensityBucketForDevice()
    return when (bestDensity) {
      "ldpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[0]), "ldpi")
      "mdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[1]), "mdpi")
      "hdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[2]), "hdpi")
      "xxhdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[4]), "xxhdpi")
      "xxxhdpi" -> Pair(getBadgeImageUri(serviceBadge.sprites6[5]), "xxxhdpi")
      else -> Pair(getBadgeImageUri(serviceBadge.sprites6[3]), "xdpi")
    }
  }

  private fun getTimestamp(bigDecimal: BigDecimal): Long {
    return Timestamp(bigDecimal.toLong() * 1000).time
  }

  @JvmStatic
  fun fromServiceBadge(serviceBadge: SignalServiceProfile.Badge): Badge {
    val uriAndDensity: Pair<Uri, String> = getBestBadgeImageUriForDevice(serviceBadge)
    return Badge(
      serviceBadge.id,
      fromCode(serviceBadge.category),
      serviceBadge.name,
      serviceBadge.description,
      uriAndDensity.first(),
      uriAndDensity.second(),
      serviceBadge.expiration?.let { getTimestamp(it) } ?: 0,
      serviceBadge.isVisible
    )
  }
}
