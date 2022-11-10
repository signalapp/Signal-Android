package org.thoughtcrime.securesms.subscription

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.lifecycle.DefaultLifecycleObserver
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.databinding.SubscriptionPreferenceBinding
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
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
      adapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, SubscriptionPreferenceBinding::inflate))
      adapter.registerFactory(LoaderModel::class.java, LayoutFactory({ LoaderViewHolder(it) }, R.layout.subscription_preference_loader))
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
    val onClick: (Subscription) -> Unit,
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

  class ViewHolder(binding: SubscriptionPreferenceBinding) : BindingViewHolder<Model, SubscriptionPreferenceBinding>(binding) {

    override fun bind(model: Model) {
      binding.root.isEnabled = model.isEnabled
      binding.root.setOnClickListener { model.onClick(model.subscription) }
      binding.root.isSelected = model.isSelected

      if (payload.isEmpty()) {
        binding.badge.setBadge(model.subscription.badge)
        binding.badge.isClickable = false
      }

      val formattedPrice = FiatMoneyUtil.format(
        context.resources,
        model.activePrice ?: model.subscription.prices.first { it.currency == model.selectedCurrency },
        FiatMoneyUtil.formatOptions().trimZerosAfterDecimal()
      )

      if (model.isActive && model.willRenew) {
        binding.tagline.text = context.getString(R.string.Subscription__renews_s, DateUtils.formatDateWithYear(Locale.getDefault(), model.renewalTimestamp))
      } else if (model.isActive) {
        binding.tagline.text = context.getString(R.string.Subscription__expires_s, DateUtils.formatDateWithYear(Locale.getDefault(), model.renewalTimestamp))
      } else {
        binding.tagline.text = context.getString(R.string.Subscription__get_a_s_badge, model.subscription.badge.name)
      }

      binding.title.text = context.getString(
        R.string.Subscription__s_per_month,
        formattedPrice
      )
      binding.check.visible = model.isActive
    }
  }
}
