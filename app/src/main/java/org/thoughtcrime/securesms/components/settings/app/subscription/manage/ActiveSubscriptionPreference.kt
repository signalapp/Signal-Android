package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Locale

/**
 * DSL renderable item that displays active subscription information on the user's
 * manage donations page.
 */
object ActiveSubscriptionPreference {

  class Model(
    val price: FiatMoney,
    val subscription: Subscription,
    val renewalTimestamp: Long = -1L,
    val redemptionState: ManageDonationsState.SubscriptionRedemptionState,
    val activeSubscription: ActiveSubscription.Subscription,
    val onContactSupport: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return subscription.id == newItem.subscription.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        subscription == newItem.subscription &&
        renewalTimestamp == newItem.renewalTimestamp &&
        redemptionState == newItem.redemptionState &&
        FiatMoney.equals(price, newItem.price) &&
        activeSubscription == newItem.activeSubscription
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    val badge: BadgeImageView = itemView.findViewById(R.id.my_support_badge)
    val title: TextView = itemView.findViewById(R.id.my_support_title)
    val expiry: TextView = itemView.findViewById(R.id.my_support_expiry)
    val progress: ProgressBar = itemView.findViewById(R.id.my_support_progress)

    override fun bind(model: Model) {
      badge.setBadge(model.subscription.badge)

      title.text = context.getString(
        R.string.MySupportPreference__s_per_month,
        FiatMoneyUtil.format(
          context.resources,
          model.price,
          FiatMoneyUtil.formatOptions()
        )
      )

      expiry.movementMethod = LinkMovementMethod.getInstance()

      when (model.redemptionState) {
        ManageDonationsState.SubscriptionRedemptionState.NONE -> presentRenewalState(model)
        ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS -> presentInProgressState()
        ManageDonationsState.SubscriptionRedemptionState.FAILED -> presentFailureState(model)
      }
    }

    private fun presentRenewalState(model: Model) {
      expiry.text = context.getString(
        R.string.MySupportPreference__renews_s,
        DateUtils.formatDateWithYear(
          Locale.getDefault(),
          model.renewalTimestamp
        )
      )
      badge.alpha = 1f
      progress.visible = false
    }

    private fun presentInProgressState() {
      expiry.text = context.getString(R.string.MySupportPreference__processing_transaction)
      badge.alpha = 0.2f
      progress.visible = true
    }

    private fun presentFailureState(model: Model) {
      if (model.activeSubscription.isFailedPayment || SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt) {
        presentPaymentFailureState(model)
      } else {
        presentRedemptionFailureState(model)
      }
    }

    private fun presentPaymentFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.DonationsErrors__error_processing_payment_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      badge.alpha = 0.2f
      progress.visible = false
    }

    private fun presentRedemptionFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.MySupportPreference__couldnt_add_badge_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      badge.alpha = 0.2f
      progress.visible = false
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.my_support_preference))
  }
}
