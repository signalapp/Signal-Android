package org.thoughtcrime.securesms.payments

import org.signal.core.util.Hex

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
  val consensus: ByteArray = Hex.fromStringCondensed(consensus)
  val report: ByteArray = Hex.fromStringCondensed(report)
  val ledger: ByteArray = Hex.fromStringCondensed(ledger)
  val view: ByteArray = Hex.fromStringCondensed(view)
}
