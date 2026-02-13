/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark.setup

import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Base64
import org.signal.core.util.UuidUtil
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration

object Harness {
  val SELF_E164 = "+15555550101"
  val SELF_ACI = ACI.from(UuidUtil.parseOrThrow("d81b9a54-0ec9-43aa-a73f-7e99280ad53e"))

  val BOB_E164 = "+15555551001"
  val BOB_ACI = ACI.from(UuidUtil.parseOrThrow("752eb667-75f6-4ed6-ade5-ca4bfd050d3d"))
  val BOB_IDENTITY_KEY = IdentityKeyPair(Base64.decode("CiEFbAw403SCGPB+tjqfk+jrH7r9ma1P2hcujqydHRYVzzISIGiWYdWYBBdBzDdF06wgEm+HKcc6ETuWB7Jnvk7Wjw1u"))
  val BOB_PROFILE_KEY = ProfileKey(Base64.decode("aJJ/A7GBCSnU9HJ1DdMWcKMMeXQKRUguTlAbtlfo/ik"))

  val trustRoot = ECKeyPair(
    ECPublicKey(Base64.decode("BVT/2gHqbrG1xzuIypLIOjFgMtihrMld1/5TGADL6Dhv")),
    ECPrivateKey(Base64.decode("2B1zU7JQdPol/XWiom4pQXrSrHFeO8jzZ1u7wfrtY3o"))
  )

  val bobClient: BobClient by lazy {
    BobClient(
      serviceId = BOB_ACI,
      e164 = BOB_E164,
      identityKeyPair = BOB_IDENTITY_KEY,
      profileKey = BOB_PROFILE_KEY
    )
  }

  fun createCertificateFor(uuid: UUID, e164: String?, deviceId: Int, identityKey: ECPublicKey, expires: Duration): SenderCertificate {
    val serverKey: ECKeyPair = ECKeyPair.generate()
    val serverCertificate = ServerCertificate(trustRoot.privateKey, 1, serverKey.publicKey)
    return serverCertificate.issue(serverKey.privateKey, uuid.toString(), Optional.ofNullable(e164), deviceId, identityKey, expires.inWholeMilliseconds)
  }
}
