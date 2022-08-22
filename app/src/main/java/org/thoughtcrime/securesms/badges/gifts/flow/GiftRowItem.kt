package org.thoughtcrime.securesms.badges.gifts.flow

import android.view.View
import android.widget.TextView
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.TimeUnit

/**
 * A line item for gifts, displayed in the Gift flow's start and confirmation fragments.
 */
object GiftRowItem {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.subscription_preference))
  }

  class Model(val giftBadge: Badge, val price: FiatMoney) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = giftBadge.id == newItem.giftBadge.id

    override fun areContentsTheSame(newItem: Model): Boolean = giftBadge == newItem.giftBadge && price == newItem.price
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val badgeView = itemView.findViewById<BadgeImageView>(R.id.badge)
    private val titleView = itemView.findViewById<TextView>(R.id.title)
    private val checkView = itemView.findViewById<View>(R.id.check)
    private val taglineView = itemView.findViewById<TextView>(R.id.tagline)
    private val priceView = itemView.findViewById<TextView>(R.id.price)

    init {
      itemView.isSelected = true
    }

    override fun bind(model: Model) {
      checkView.visible = false
      badgeView.setBadge(model.giftBadge)
      titleView.text = model.giftBadge.name
      taglineView.setText(R.string.GiftRowItem__send_a_gift_badge)

      val price = FiatMoneyUtil.format(
        context.resources,
        model.price,
        FiatMoneyUtil.formatOptions()
          .trimZerosAfterDecimal()
          .withDisplayTime(false)
      )

      val duration = TimeUnit.MILLISECONDS.toDays(model.giftBadge.duration)

      priceView.text = context.resources.getQuantityString(R.plurals.GiftRowItem_s_dot_d_day_duration, duration.toInt(), price, duration)
    }
  }
}
