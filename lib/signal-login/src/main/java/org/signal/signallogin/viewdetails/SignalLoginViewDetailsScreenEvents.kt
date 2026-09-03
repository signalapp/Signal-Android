/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import org.signal.core.util.censor

sealed class SignalLoginViewDetailsScreenEvents {
  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginViewDetailsScreenEvents()

  /** The user chose to store the credentials with the system password manager. */
  data object SaveToPasswordManagerClicked : SignalLoginViewDetailsScreenEvents()

  /** The user chose to save the credentials as a PDF. */
  data object SaveAsPdfClicked : SignalLoginViewDetailsScreenEvents()

  /** User long clicked the account ID field. */
  data class AccountIdLongClicked(val aci: String) : SignalLoginViewDetailsScreenEvents() {
    override fun toString(): String {
      return "AccountIdLongClicked(aci=${aci.censor()})"
    }
  }

  /** User long clicked the recovery key field. */
  data class RecoveryKeyLongClicked(val aep: String) : SignalLoginViewDetailsScreenEvents() {
    override fun toString(): String {
      return "RecoveryKeyLongClicked(aep=${aep.censor()})"
    }
  }
}
