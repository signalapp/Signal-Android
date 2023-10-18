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
  VAN_LANCHOT("van_lanchot"),
  YOURSAFE("yoursafe");

  fun getUIValues(): UIValues = bankToUIValues[this]!!

  companion object {

    private val bankToUIValues: Map<IdealBank, UIValues> by lazy {
      EnumMap<IdealBank, UIValues>(IdealBank::class.java).apply {
        putAll(
          arrayOf(
            ABN_AMRO to UIValues(
              name = R.string.IdealBank__abn_amro,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            ASN_BANK to UIValues(
              name = R.string.IdealBank__asn_bank,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            BUNQ to UIValues(
              name = R.string.IdealBank__bunq,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            ING to UIValues(
              name = R.string.IdealBank__ing,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            KNAB to UIValues(
              name = R.string.IdealBank__knab,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            N26 to UIValues(
              name = R.string.IdealBank__n26,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            RABOBANK to UIValues(
              name = R.string.IdealBank__rabobank,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            REGIOBANK to UIValues(
              name = R.string.IdealBank__regiobank,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            REVOLUT to UIValues(
              name = R.string.IdealBank__revolut,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            SNS_BANK to UIValues(
              name = R.string.IdealBank__sns_bank,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            TRIODOS_BANK to UIValues(
              name = R.string.IdealBank__triodos_bank,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            VAN_LANCHOT to UIValues(
              name = R.string.IdealBank__van_lanchot,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            ),
            YOURSAFE to UIValues(
              name = R.string.IdealBank__yoursafe,
              icon = R.drawable.ic_person_large // TODO [sepa] -- final icon
            )
          )
        )
      }
    }

    fun fromCode(code: String): IdealBank {
      return values().first { it.code == code }
    }
  }

  data class UIValues(
    @StringRes val name: Int,
    @DrawableRes val icon: Int
  )
}
