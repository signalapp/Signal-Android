package org.thoughtcrime.securesms.badges

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.DSLConfiguration

object Badges {
  fun DSLConfiguration.displayBadges(context: Context, badges: List<Badge>, selectedBadge: Badge? = null) {
    badges
      .map { Badge.Model(it, it == selectedBadge) }
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
}
