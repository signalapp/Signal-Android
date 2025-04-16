package org.whispersystems.signalservice.api.groupsv2

import org.signal.libsignal.zkgroup.ServerPublicParams
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.groups.GroupPublicParams
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredentialResponse
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCommitment
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest
import org.signal.libsignal.zkgroup.profiles.ServerZkProfileOperations
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.time.Instant

/**
 * Provides Zk group operations that the server would provide.
 */
internal class TestZkGroupServer {
  @JvmField
  val serverPublicParams: ServerPublicParams
  private val serverZkProfileOperations: ServerZkProfileOperations

  init {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val serverSecretParams = ServerSecretParams.generate()

    serverPublicParams = serverSecretParams.publicParams
    serverZkProfileOperations = ServerZkProfileOperations(serverSecretParams)
  }

  fun getExpiringProfileKeyCredentialResponse(request: ProfileKeyCredentialRequest?, aci: ACI, commitment: ProfileKeyCommitment?, expiration: Instant?): ExpiringProfileKeyCredentialResponse {
    return serverZkProfileOperations.issueExpiringProfileKeyCredential(request, aci.libSignalAci, commitment, expiration)
  }

  fun assertProfileKeyCredentialPresentation(publicParams: GroupPublicParams?, profileKeyCredentialPresentation: ProfileKeyCredentialPresentation?, now: Instant?) {
    try {
      serverZkProfileOperations.verifyProfileKeyCredentialPresentation(publicParams, profileKeyCredentialPresentation, now)
    } catch (e: VerificationFailedException) {
      throw AssertionError(e)
    }
  }
}
