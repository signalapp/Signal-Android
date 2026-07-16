/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.donation.DonationPermit
import org.signal.libsignal.zkgroup.donation.DonationPermitDerivedKeyPair
import org.signal.libsignal.zkgroup.donation.DonationPermitRequest
import org.signal.libsignal.zkgroup.donation.DonationPermitRequestContext
import org.signal.libsignal.zkgroup.donation.DonationPermitResponse
import java.time.Instant

/**
 * Runs the real zkgroup issuance flow so tests exercise genuine [DonationPermit]s rather than fabricated bytes.
 */
class TestDonationPermitServer(private val secret: ServerSecretParams = ServerSecretParams.generate()) {

  val publicParams = secret.publicParams

  /** Mints [count] permits dated to [now]'s default expiration via the full client + server round-trip. */
  fun mint(count: Int, now: Instant): List<DonationPermit> {
    val context = DonationPermitRequestContext.forCount(count)
    val responseBytes = issue(context.request().serialize(), now)
    return context.receive(DonationPermitResponse(responseBytes), publicParams, now)
  }

  /** Server-side issuance: blindly signs [requestBytes] for [now]'s default expiration. */
  fun issue(requestBytes: ByteArray, now: Instant): ByteArray {
    val expiration = DonationPermitResponse.defaultExpiration(now)
    val keyPair = DonationPermitDerivedKeyPair.forExpiration(expiration, secret)
    return DonationPermitRequest(requestBytes).issue(keyPair).serialize()
  }
}

/**
 * A [DonationPermitIssuer] that issues against a [TestDonationPermitServer], with hooks to simulate failures.
 */
class TestDonationPermitIssuer(
  private val server: TestDonationPermitServer,
  private val now: Instant,
  var error: DonationPermitError? = null,
  var returnMalformed: Boolean = false,
  /** Invoked while a request is "in flight", to simulate work (e.g. a clear) racing the network call. */
  var onIssue: () -> Unit = {}
) : DonationPermitIssuer {

  var issueCount: Int = 0
    private set

  override fun issue(requestBytes: ByteArray): Either<DonationPermitError, ByteArray> {
    issueCount++
    onIssue()
    error?.let { return it.left() }
    return (if (returnMalformed) byteArrayOf(1, 2, 3) else server.issue(requestBytes, now)).right()
  }
}
