package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import java.util.Locale

object DonationReceiptListItem {

  fun register(adapter: MappingAdapter, onClick: (Model) -> Unit) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onClick) }, R.layout.donation_receipt_list_item))
  }

  class Model(
    val record: InAppPaymentReceiptRecord,
    val badge: Badge?
  ) : MappingModel<Model> {
    override fun areContentsTheSame(newItem: Model): Boolean = record == newItem.record && badge == newItem.badge

    override fun areItemsTheSame(newItem: Model): Boolean = record.id == newItem.record.id
  }

  private class ViewHolder(itemView: View, private val onClick: (Model) -> Unit) : MappingViewHolder<Model>(itemView) {

    private val badgeView: BadgeImageView = itemView.findViewById(R.id.badge)
    private val dateView: TextView = itemView.findViewById(R.id.date)
    private val typeView: TextView = itemView.findViewById(R.id.type)
    private val moneyView: TextView = itemView.findViewById(R.id.money)

    override fun bind(model: Model) {
      itemView.setOnClickListener { onClick(model) }
      badgeView.setBadge(model.badge)
      dateView.text = DateUtils.formatDate(Locale.getDefault(), model.record.timestamp)
      typeView.setText(
        when (model.record.type) {
          InAppPaymentReceiptRecord.Type.RECURRING_DONATION -> R.string.DonationReceiptListFragment__recurring
          InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION -> R.string.DonationReceiptListFragment__one_time
          InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT -> R.string.DonationReceiptListFragment__donation_for_a_friend
          InAppPaymentReceiptRecord.Type.RECURRING_BACKUP -> error("Not supported in this fragment")
        }
      )
      moneyView.text = FiatMoneyUtil.format(context.resources, model.record.amount)
    }
  }
}
