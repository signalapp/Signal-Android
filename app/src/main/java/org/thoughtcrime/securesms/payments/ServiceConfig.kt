package org.thoughtcrime.securesms.payments

import com.mobilecoin.lib.util.Hex

/**
 * Represents the service configuration values for a given MobileCoin config, used to build
 * Verifiers.
 */
class ServiceConfig(
  consensus: String,
  report: String,
  ledger: String,
  view: String,
  val hardeningAdvisories: Array<String>
) {
  val consensus: ByteArray = Hex.toByteArray(consensus)
  val report: ByteArray = Hex.toByteArray(report)
  val ledger: ByteArray = Hex.toByteArray(ledger)
  val view: ByteArray = Hex.toByteArray(view)
}
