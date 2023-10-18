/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import android.widget.ProgressBar
import android.widget.TextView
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.PendingOneTimeDonationSerializer.toFiatMoney
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.databinding.MySupportPreferenceBinding
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.visible

/**
 * Holds state information about pending one-time donations.
 */
object OneTimeDonationPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, MySupportPreferenceBinding::inflate))
  }

  class Model(
    val pendingOneTimeDonation: PendingOneTimeDonation,
    val onPendingClick: (FiatMoney) -> Unit
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean {
      return this.pendingOneTimeDonation == newItem.pendingOneTimeDonation
    }
  }

  class ViewHolder(binding: MySupportPreferenceBinding) : BindingViewHolder<Model, MySupportPreferenceBinding>(binding) {

    val badge: BadgeImageView = binding.mySupportBadge
    val title: TextView = binding.mySupportTitle
    val expiry: TextView = binding.mySupportExpiry
    val progress: ProgressBar = binding.mySupportProgress

    override fun bind(model: Model) {
      badge.setBadge(Badges.fromDatabaseBadge(model.pendingOneTimeDonation.badge!!))
      title.text = context.getString(
        R.string.OneTimeDonationPreference__one_time_s,
        FiatMoneyUtil.format(context.resources, model.pendingOneTimeDonation.amount!!.toFiatMoney(), FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      )

      expiry.text = getPendingSubtitle(model.pendingOneTimeDonation.paymentMethodType)

      if (model.pendingOneTimeDonation.paymentMethodType == PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT) {
        itemView.setOnClickListener { model.onPendingClick(model.pendingOneTimeDonation.amount.toFiatMoney()) }
      }

      progress.visible = model.pendingOneTimeDonation.paymentMethodType != PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT
    }

    private fun getPendingSubtitle(paymentMethodType: PendingOneTimeDonation.PaymentMethodType): String {
      return when (paymentMethodType) {
        PendingOneTimeDonation.PaymentMethodType.CARD -> context.getString(R.string.OneTimeDonationPreference__donation_processing)
        PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT -> context.getString(R.string.OneTimeDonationPreference__donation_pending)
        PendingOneTimeDonation.PaymentMethodType.PAYPAL -> context.getString(R.string.OneTimeDonationPreference__donation_processing)
        PendingOneTimeDonation.PaymentMethodType.IDEAL -> context.getString(R.string.OneTimeDonationPreference__donation_pending)
      }
    }
  }
}
