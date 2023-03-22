package org.thoughtcrime.securesms.badges.gifts

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.button.MaterialButton
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.gifts.Gifts.formatExpiry
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Displays a gift badge sent to or received from a user, and allows the user to
 * perform an action based off the badge's redemption state.
 */
class GiftMessageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
  init {
    inflate(context, R.layout.gift_message_view, this)
  }

  private val badgeView: BadgeImageView = findViewById(R.id.gift_message_view_badge)
  private val titleView: TextView = findViewById(R.id.gift_message_view_title)
  private val descriptionView: TextView = findViewById(R.id.gift_message_view_description)
  private val actionView: MaterialButton = findViewById(R.id.gift_message_view_action)

  init {
    context.obtainStyledAttributes(attrs, R.styleable.GiftMessageView).use {
      val textColor = it.getColor(R.styleable.GiftMessageView_giftMessageView__textColor, Color.RED)
      titleView.setTextColor(textColor)
      descriptionView.setTextColor(textColor)

      val buttonTextColor = it.getColor(R.styleable.GiftMessageView_giftMessageView__buttonTextColor, Color.RED)
      actionView.setTextColor(buttonTextColor)
      actionView.iconTint = ColorStateList.valueOf(buttonTextColor)

      val buttonBackgroundTint = it.getColor(R.styleable.GiftMessageView_giftMessageView__buttonBackgroundTint, Color.RED)
      actionView.backgroundTintList = ColorStateList.valueOf(buttonBackgroundTint)
    }
  }

  fun setGiftBadge(glideRequests: GlideRequests, giftBadge: GiftBadge, isOutgoing: Boolean, callback: Callback, recipient: Recipient) {
    descriptionView.text = giftBadge.formatExpiry(context)
    actionView.icon = null
    actionView.setOnClickListener { callback.onViewGiftBadgeClicked() }
    actionView.isEnabled = true

    if (isOutgoing) {
      actionView.setText(R.string.GiftMessageView__view)
      titleView.text = context.getString(R.string.GiftMessageView__donation_on_behalf_of_s, recipient.getDisplayName(context))
    } else {
      titleView.text = context.getString(R.string.GiftMessageView__s_donated_to_signal_on, recipient.getShortDisplayName(context))
      when (giftBadge.redemptionState) {
        GiftBadge.RedemptionState.REDEEMED -> {
          stopAnimationIfNeeded()
          actionView.setIconResource(R.drawable.ic_check_circle_24)
        }
        GiftBadge.RedemptionState.STARTED -> actionView.icon = CircularProgressDrawable(context).apply {
          actionView.isEnabled = false
          setColorSchemeColors(ContextCompat.getColor(context, R.color.core_ultramarine))
          strokeWidth = DimensionUnit.DP.toPixels(2f)
          start()
        }
        else -> {
          stopAnimationIfNeeded()
          actionView.icon = null
        }
      }

      actionView.setText(
        when (giftBadge.redemptionState ?: GiftBadge.RedemptionState.UNRECOGNIZED) {
          GiftBadge.RedemptionState.PENDING -> R.string.GiftMessageView__redeem
          GiftBadge.RedemptionState.STARTED -> R.string.GiftMessageView__redeeming
          GiftBadge.RedemptionState.REDEEMED -> R.string.GiftMessageView__redeemed
          GiftBadge.RedemptionState.FAILED -> R.string.GiftMessageView__redeem
          GiftBadge.RedemptionState.UNRECOGNIZED -> R.string.GiftMessageView__redeem
        }
      )
    }

    badgeView.setGiftBadge(giftBadge, glideRequests)
  }

  fun onGiftNotOpened() {
    actionView.isClickable = false
  }

  fun onGiftOpened() {
    actionView.isClickable = true
  }

  private fun stopAnimationIfNeeded() {
    val icon = actionView.icon
    if (icon is CircularProgressDrawable) {
      icon.stop()
    }
  }

  interface Callback {
    fun onViewGiftBadgeClicked()
  }
}
