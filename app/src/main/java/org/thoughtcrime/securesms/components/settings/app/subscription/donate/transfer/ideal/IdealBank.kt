/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R
import java.util.EnumMap

/**
 * Set of banks that are supported for iDEAL transfers, as listed here:
 * https://stripe.com/docs/api/payment_methods/object#payment_method_object-ideal-bank
 */
enum class IdealBank(
  val code: String
) {
  ABN_AMRO("abn_amro"),
  ASN_BANK("asn_bank"),
  BUNQ("bunq"),
  ING("ing"),
  KNAB("knab"),
  N26("n26"),
  RABOBANK("rabobank"),
  REGIOBANK("regiobank"),
  REVOLUT("revolut"),
  SNS_BANK("sns_bank"),
  TRIODOS_BANK("triodos_bank"),
  VAN_LANSCHOT("van_lanschot"),
  YOURSAFE("yoursafe");

  fun getUIValues(): UIValues = bankToUIValues[this]!!

  companion object {

    private val bankToUIValues: Map<IdealBank, UIValues> by lazy {
      EnumMap<IdealBank, UIValues>(IdealBank::class.java).apply {
        putAll(
          arrayOf(
            ABN_AMRO to UIValues(
              name = R.string.IdealBank__abn_amro,
              icon = R.drawable.ideal_abn_amro
            ),
            ASN_BANK to UIValues(
              name = R.string.IdealBank__asn_bank,
              icon = R.drawable.ideal_asn
            ),
            BUNQ to UIValues(
              name = R.string.IdealBank__bunq,
              icon = R.drawable.ideal_bunq
            ),
            ING to UIValues(
              name = R.string.IdealBank__ing,
              icon = R.drawable.ideal_ing
            ),
            KNAB to UIValues(
              name = R.string.IdealBank__knab,
              icon = R.drawable.ideal_knab
            ),
            N26 to UIValues(
              name = R.string.IdealBank__n26,
              icon = R.drawable.ideal_n26
            ),
            RABOBANK to UIValues(
              name = R.string.IdealBank__rabobank,
              icon = R.drawable.ideal_rabobank
            ),
            REGIOBANK to UIValues(
              name = R.string.IdealBank__regiobank,
              icon = R.drawable.ideal_regiobank
            ),
            REVOLUT to UIValues(
              name = R.string.IdealBank__revolut,
              icon = R.drawable.ideal_revolut
            ),
            SNS_BANK to UIValues(
              name = R.string.IdealBank__sns_bank,
              icon = R.drawable.ideal_sns
            ),
            TRIODOS_BANK to UIValues(
              name = R.string.IdealBank__triodos_bank,
              icon = R.drawable.ideal_triodos_bank
            ),
            VAN_LANSCHOT to UIValues(
              name = R.string.IdealBank__van_lanschot,
              icon = R.drawable.ideal_van_lanschot
            ),
            YOURSAFE to UIValues(
              name = R.string.IdealBank__yoursafe,
              icon = R.drawable.ideal_yoursafe
            )
          )
        )
      }
    }

    fun fromCode(code: String): IdealBank {
      return entries.first { it.code == code }
    }
  }

  data class UIValues(
    @StringRes val name: Int,
    @DrawableRes val icon: Int
  )
}
