package org.thoughtcrime.securesms.subscription

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.lifecycle.DefaultLifecycleObserver
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Currency
import java.util.Locale

/**
 * Represents a Subscription that a user can start.
 */
data class Subscription(
  val id: String,
  val name: String,
  val badge: Badge,
  val prices: Set<FiatMoney>,
  val level: Int,
) {

  companion object {
    fun register(adapter: MappingAdapter) {
      adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.subscription_preference))
      adapter.registerFactory(LoaderModel::class.java, MappingAdapter.LayoutFactory({ LoaderViewHolder(it) }, R.layout.subscription_preference_loader))
    }
  }

  class LoaderModel : PreferenceModel<LoaderModel>() {
    override fun areItemsTheSame(newItem: LoaderModel): Boolean = true
  }

  class LoaderViewHolder(itemView: View) : MappingViewHolder<LoaderModel>(itemView), DefaultLifecycleObserver {

    private val animator: Animator = AnimatorSet().apply {
      val fadeTo25Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0.8f, 0.25f).apply {
        duration = 1000L
      }

      val fadeTo80Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0.25f, 0.8f).apply {
        duration = 300L
      }

      playSequentially(fadeTo25Animator, fadeTo80Animator)
      doOnEnd {
        if (itemView.isAttachedToWindow) {
          start()
        }
      }
    }

    override fun bind(model: LoaderModel) {
    }

    override fun onAttachedToWindow() {
      if (animator.isStarted) {
        animator.resume()
      } else {
        animator.start()
      }
    }

    override fun onDetachedFromWindow() {
      animator.pause()
    }
  }

  class Model(
    val activePrice: FiatMoney?,
    val subscription: Subscription,
    val isSelected: Boolean,
    val isActive: Boolean,
    val willRenew: Boolean,
    override val isEnabled: Boolean,
    val onClick: () -> Unit,
    val renewalTimestamp: Long,
    val selectedCurrency: Currency
  ) : PreferenceModel<Model>(isEnabled = isEnabled) {

    override fun areItemsTheSame(newItem: Model): Boolean {
      return subscription.id == newItem.subscription.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.subscription == subscription &&
        newItem.isSelected == isSelected &&
        newItem.isActive == isActive &&
        newItem.renewalTimestamp == renewalTimestamp &&
        newItem.willRenew == willRenew &&
        newItem.selectedCurrency == selectedCurrency
    }

    override fun getChangePayload(newItem: Model): Any? {
      return if (newItem.subscription.badge == subscription.badge) {
        Unit
      } else {
        null
      }
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)
    private val title: TextView = itemView.findViewById(R.id.title)
    private val tagline: TextView = itemView.findViewById(R.id.tagline)
    private val price: TextView = itemView.findViewById(R.id.price)
    private val check: ImageView = itemView.findViewById(R.id.check)

    override fun bind(model: Model) {
      itemView.isEnabled = model.isEnabled
      itemView.setOnClickListener { model.onClick() }
      itemView.isSelected = model.isSelected

      if (payload.isEmpty()) {
        badge.setBadge(model.subscription.badge)
        badge.isClickable = false
      }

      title.text = model.subscription.name
      tagline.text = context.getString(R.string.Subscription__earn_a_s_badge, model.subscription.badge.name)

      val formattedPrice = FiatMoneyUtil.format(
        context.resources,
        model.activePrice ?: model.subscription.prices.first { it.currency == model.selectedCurrency },
        FiatMoneyUtil.formatOptions()
      )

      if (model.isActive && model.willRenew) {
        price.text = context.getString(
          R.string.Subscription__s_per_month_dot_renews_s,
          formattedPrice,
          DateUtils.formatDateWithYear(Locale.getDefault(), model.renewalTimestamp)
        )
      } else if (model.isActive) {
        price.text = context.getString(
          R.string.Subscription__s_per_month_dot_expires_s,
          formattedPrice,
          DateUtils.formatDateWithYear(Locale.getDefault(), model.renewalTimestamp)
        )
      } else {
        price.text = context.getString(
          R.string.Subscription__s_per_month,
          formattedPrice
        )
      }

      check.visible = model.isActive
    }
  }
}
