package org.signal.devicetransfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.signal.devicetransfer.DeviceTransferAuthentication.DeviceTransferAuthenticationException
import org.whispersystems.signalservice.test.LibSignalLibraryUtil
import kotlin.random.Random

class DeviceTransferAuthenticationTest {
  private lateinit var certificate: ByteArray

  @Before
  fun ensureNativeSupported() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    certificate = SelfSignedIdentity.create().x509Encoded
  }

  @Test
  fun testCompute_withNoChanges() {
    val client = DeviceTransferAuthentication.Client(certificate)
    val server = DeviceTransferAuthentication.Server(certificate, client.commitment)

    val clientRandom = client.setServerRandomAndGetClientRandom(server.random)

    server.setClientRandom(clientRandom)
    assertEquals(client.computeShortAuthenticationCode(), server.computeShortAuthenticationCode())
  }

  @Test(expected = DeviceTransferAuthenticationException::class)
  fun testServerCompute_withChangedClientCertificate() {
    val badCertificate = SelfSignedIdentity.create().x509Encoded
    val client = DeviceTransferAuthentication.Client(badCertificate)
    val server = DeviceTransferAuthentication.Server(certificate, client.commitment)

    val clientRandom = client.setServerRandomAndGetClientRandom(server.random)

    server.setClientRandom(clientRandom)
    server.computeShortAuthenticationCode()
  }

  @Test(expected = DeviceTransferAuthenticationException::class)
  fun testServerCompute_withChangedClientCommitment() {
    val client = DeviceTransferAuthentication.Client(certificate)
    val server = DeviceTransferAuthentication.Server(certificate, randomBytes())

    val clientRandom = client.setServerRandomAndGetClientRandom(server.random)

    server.setClientRandom(clientRandom)
    server.computeShortAuthenticationCode()
  }

  @Test(expected = DeviceTransferAuthenticationException::class)
  fun testServerCompute_withChangedClientRandom() {
    val client = DeviceTransferAuthentication.Client(certificate)
    val server = DeviceTransferAuthentication.Server(certificate, client.commitment)

    client.setServerRandomAndGetClientRandom(server.random)

    server.setClientRandom(randomBytes())
    server.computeShortAuthenticationCode()
  }

  @Test
  fun testClientCompute_withChangedServerSecret() {
    val client = DeviceTransferAuthentication.Client(certificate)
    val server = DeviceTransferAuthentication.Server(certificate, client.commitment)

    val clientRandom = client.setServerRandomAndGetClientRandom(randomBytes())

    server.setClientRandom(clientRandom)
    assertNotEquals(client.computeShortAuthenticationCode(), server.computeShortAuthenticationCode())
  }

  private fun randomBytes(): ByteArray {
    val bytes = ByteArray(32)
    Random.nextBytes(bytes)
    return bytes
  }
}
