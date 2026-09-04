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

  /** The user tapped the copy button on the account ID field. */
  data class CopyAccountIdClicked(val aci: String) : SignalLoginViewDetailsScreenEvents() {
    override fun toString(): String {
      return "CopyAccountIdClicked(aci=${aci.censor()})"
    }
  }

  /** The user tapped the copy button on the recovery key field. */
  data class CopyRecoveryKeyClicked(val aep: String) : SignalLoginViewDetailsScreenEvents() {
    override fun toString(): String {
      return "CopyRecoveryKeyClicked(aep=${aep.censor()})"
    }
  }
}
