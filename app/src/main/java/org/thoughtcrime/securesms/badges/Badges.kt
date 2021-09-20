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
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgeAnimator
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.util.customizeOnDraw

object Badges {
  fun Drawable.insetWithOutline(
    @Px outlineWidth: Float,
    @ColorInt outlineColor: Int
  ): Drawable {
    val clone = mutate().constantState?.newDrawable()?.mutate()
    clone?.colorFilter = SimpleColorFilter(outlineColor)

    return customizeOnDraw { wrapped, canvas ->
      clone?.bounds = wrapped.bounds
      clone?.draw(canvas)

      val scale = 1 - ((outlineWidth * 2) / canvas.width)

      canvas.withScale(x = scale, y = scale, canvas.width / 2f, canvas.height / 2f) {
        wrapped.draw(canvas)
      }
    }
  }

  fun Drawable.selectable(
    @Px outlineWidth: Float,
    @ColorInt outlineColor: Int,
    @ColorInt gapColor: Int,
    animator: BadgeAnimator
  ): Drawable {
    val outline = mutate().constantState?.newDrawable()?.mutate()
    outline?.colorFilter = SimpleColorFilter(outlineColor)

    val gap = mutate().constantState?.newDrawable()?.mutate()
    gap?.colorFilter = SimpleColorFilter(gapColor)

    return customizeOnDraw { wrapped, canvas ->
      outline?.bounds = wrapped.bounds
      gap?.bounds = wrapped.bounds

      outline?.draw(canvas)

      val scale = 1 - ((outlineWidth * 2) / wrapped.bounds.width())
      val interpolatedScale = scale + (1f - scale) * animator.getFraction()

      canvas.withScale(x = interpolatedScale, y = interpolatedScale, wrapped.bounds.width() / 2f, wrapped.bounds.height() / 2f) {
        gap?.draw(canvas)

        canvas.withScale(x = interpolatedScale, y = interpolatedScale, wrapped.bounds.width() / 2f, wrapped.bounds.height() / 2f) {
          wrapped.draw(canvas)
        }
      }

      if (animator.shouldInvalidate()) {
        invalidateSelf()
      }
    }
  }

  fun DSLConfiguration.displayBadges(badges: List<Badge>, selectedBadge: Badge? = null) {
    badges
      .map { Badge.Model(it, it == selectedBadge) }
      .forEach { customPref(it) }

    val empties = (4 - (badges.size % 4)) % 4
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
