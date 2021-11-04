package org.thoughtcrime.securesms.badges

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.use
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.glide.BadgeSpriteTransformation
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ThemeUtil
import java.lang.IllegalArgumentException

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
    getGlideRequests()?.let {
      setBadgeFromRecipient(recipient, it)
    } ?: setImageDrawable(null)
  }

  fun setBadgeFromRecipient(recipient: Recipient?, glideRequests: GlideRequests) {
    if (recipient == null || recipient.badges.isEmpty()) {
      setBadge(null, glideRequests)
    } else {
      setBadge(recipient.badges[0], glideRequests)
    }
  }

  fun setBadge(badge: Badge?) {
    getGlideRequests()?.let {
      setBadge(badge, it)
    } ?: setImageDrawable(null)
  }

  fun setBadge(badge: Badge?, glideRequests: GlideRequests) {
    if (badge != null) {
      glideRequests
        .load(badge)
        .downsample(DownsampleStrategy.NONE)
        .transform(BadgeSpriteTransformation(BadgeSpriteTransformation.Size.fromInteger(badgeSize), badge.imageDensity, ThemeUtil.isDarkTheme(context)))
        .into(this)
    } else {
      glideRequests
        .clear(this)
      setImageDrawable(null)
    }
  }

  private fun getGlideRequests(): GlideRequests? {
    return try {
      GlideApp.with(this)
    } catch (e: IllegalArgumentException) {
      // View not attached to an activity or activity destroyed
      null
    }
  }
}
