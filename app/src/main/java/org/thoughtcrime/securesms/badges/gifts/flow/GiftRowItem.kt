package org.thoughtcrime.securesms.badges.gifts.flow

import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.databinding.SubscriptionPreferenceBinding
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.TimeUnit

/**
 * A line item for gifts, displayed in the Gift flow's start and confirmation fragments.
 */
object GiftRowItem {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, SubscriptionPreferenceBinding::inflate))
  }

  class Model(val giftBadge: Badge, val price: FiatMoney) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = giftBadge.id == newItem.giftBadge.id

    override fun areContentsTheSame(newItem: Model): Boolean = giftBadge == newItem.giftBadge && price == newItem.price
  }

  class ViewHolder(binding: SubscriptionPreferenceBinding) : BindingViewHolder<Model, SubscriptionPreferenceBinding>(binding) {
    init {
      binding.root.isSelected = true
    }

    override fun bind(model: Model) {
      binding.check.visible = false
      binding.badge.setBadge(model.giftBadge)
      binding.tagline.visible = false

      val price = FiatMoneyUtil.format(
        context.resources,
        model.price,
        FiatMoneyUtil.formatOptions()
          .trimZerosAfterDecimal()
          .withDisplayTime(false)
      )

      val duration = TimeUnit.MILLISECONDS.toDays(model.giftBadge.duration)

      binding.title.text = context.resources.getQuantityString(R.plurals.GiftRowItem_s_dot_d_day_duration, duration.toInt(), price, duration)
    }
  }
}
