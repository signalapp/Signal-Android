/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark.setup

import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.UuidUtil
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import java.util.Optional
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration

object Harness {
  const val SELF_E164 = "+15555559999"
  val SELF_ACI = ACI.from(UuidUtil.parseOrThrow("d81b9a54-0ec9-43aa-a73f-7e99280ad53e"))

  private val OTHERS_IDENTITY_KEY = IdentityKeyPair(Base64.decode("CiEFbAw403SCGPB+tjqfk+jrH7r9ma1P2hcujqydHRYVzzISIGiWYdWYBBdBzDdF06wgEm+HKcc6ETuWB7Jnvk7Wjw1u"))
  private val OTHERS_PROFILE_KEY = ProfileKey(Base64.decode("aJJ/A7GBCSnU9HJ1DdMWcKMMeXQKRUguTlAbtlfo/ik"))

  val groupMasterKey = GroupMasterKey(Hex.fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))

  val trustRoot = ECKeyPair(
    ECPublicKey(Base64.decode("BVT/2gHqbrG1xzuIypLIOjFgMtihrMld1/5TGADL6Dhv")),
    ECPrivateKey(Base64.decode("2B1zU7JQdPol/XWiom4pQXrSrHFeO8jzZ1u7wfrtY3o"))
  )

  val otherClients: List<OtherClient> by lazy {
    val random = Random(4242)
    buildList {
      (0 until 1000).forEach { i ->
        val aci = ACI.from(UUID(random.nextLong(), random.nextLong()))
        val e164 = "+1555555%04d".format(i)
        val identityKey = OTHERS_IDENTITY_KEY
        val profileKey = OTHERS_PROFILE_KEY

        add(OtherClient(aci, e164, identityKey, profileKey))
      }
    }
  }

  fun createCertificateFor(uuid: UUID, e164: String?, deviceId: Int, identityKey: ECPublicKey, expires: Duration): SenderCertificate {
    val serverKey: ECKeyPair = ECKeyPair.generate()
    val serverCertificate = ServerCertificate(trustRoot.privateKey, 1, serverKey.publicKey)
    return serverCertificate.issue(serverKey.privateKey, uuid.toString(), Optional.ofNullable(e164), deviceId, identityKey, expires.inWholeMilliseconds)
  }
}
