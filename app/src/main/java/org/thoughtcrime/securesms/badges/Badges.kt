package org.thoughtcrime.securesms.badges

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.core.graphics.withScale
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgeAnimator
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.util.customizeOnDraw

object Badges {
  fun Drawable.selectable(
    @Px outlineWidth: Float,
    @ColorInt outlineColor: Int,
    animator: BadgeAnimator
  ): Drawable {
    val outline = mutate().constantState?.newDrawable()?.mutate()
    outline?.colorFilter = SimpleColorFilter(outlineColor)

    return customizeOnDraw { wrapped, canvas ->
      outline?.bounds = wrapped.bounds

      outline?.draw(canvas)

      val scale = 1 - ((outlineWidth * 2) / wrapped.bounds.width())
      val interpolatedScale = scale + (1f - scale) * animator.getFraction()

      canvas.withScale(x = interpolatedScale, y = interpolatedScale, wrapped.bounds.width() / 2f, wrapped.bounds.height() / 2f) {
        wrapped.draw(canvas)
      }

      if (animator.shouldInvalidate()) {
        invalidateSelf()
      }
    }
  }

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
