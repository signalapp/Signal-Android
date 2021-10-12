package org.thoughtcrime.securesms.badges

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.use
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.glide.BadgeSpriteTransformation
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

private val TAG = Log.tag(BadgeImageView::class.java)

class BadgeImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  private var badgeSize: Int = 0

  init {
    context.obtainStyledAttributes(attrs, R.styleable.BadgeImageView).use {
      badgeSize = it.getInt(R.styleable.BadgeImageView_badge_size, 0)
    }
  }

  fun setBadgeFromRecipient(recipient: Recipient?) {
    if (recipient == null || recipient.badges.isEmpty()) {
      setBadge(null)
    } else {
      setBadge(recipient.badges[0])
    }
  }

  fun setBadge(badge: Badge?) {
    visible = badge != null

    val lifecycle = ViewUtil.getActivityLifecycle(this)
    if (lifecycle?.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    if (badge != null) {
      GlideApp
        .with(this)
        .load(badge)
        .downsample(DownsampleStrategy.NONE)
        .transform(BadgeSpriteTransformation(BadgeSpriteTransformation.Size.fromInteger(badgeSize), badge.imageDensity, ThemeUtil.isDarkTheme(context)))
        .into(this)
    } else {
      GlideApp
        .with(this)
        .clear(this)
    }
  }
}
