/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class BlacklistingTrustManagerTest {

  private val delegate = mockk<X509TrustManager>(relaxed = true)
  private val trustManager = BlacklistingTrustManager(delegate)

  @Test
  fun `blacklist matches the published eMudhra intermediates`() {
    val intermediates = loadImpactedIntermediates()
    assertThat(intermediates).hasSize(16)

    val publishedKeys = intermediates.map { it.spkiSha256.lowercase() }.toSet()

    assertThat(BlacklistingTrustManager.BLACKLISTED_PUBLIC_KEY_SHA256).isEqualTo(publishedKeys)
  }

  @Test
  fun `every published eMudhra intermediate is rejected even when the delegate trusts it`() {
    for (intermediate in loadImpactedIntermediates()) {
      val failure = runCatching { trustManager.checkServerTrusted(arrayOf(intermediate.certificate()), AUTH_TYPE) }.exceptionOrNull()

      assertThat(failure, "${intermediate.name} via ${intermediate.issuerOperator}").isNotNull().isInstanceOf<CertificateException>()
    }
  }

  @Test
  fun `a rejected intermediate cannot poison the delegate cache`() {
    val cachingDelegate = CachingTrustManager()
    val trustManager = BlacklistingTrustManager(cachingDelegate)
    val leaf = readCertificate("ias.cert")
    val compromisedIntermediate = loadImpactedIntermediates().first().certificate()

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(leaf, compromisedIntermediate), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
    assertThat(cachingDelegate.hasCachedIntermediate).isFalse()

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(leaf), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
  }

  @Test
  fun `a certificate that is not blacklisted is accepted and the delegate is consulted`() {
    val certificate = readCertificate("ias.cert")

    trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE)

    verify { delegate.checkServerTrusted(any(), AUTH_TYPE) }
  }

  @Test
  fun `a rejection from the delegate is propagated`() {
    every { delegate.checkServerTrusted(any(), any()) } throws CertificateException("untrusted")

    assertFailure {
      trustManager.checkServerTrusted(arrayOf(readCertificate("ias.cert")), AUTH_TYPE)
    }.isInstanceOf<CertificateException>()
  }

  private fun loadImpactedIntermediates(): List<ImpactedIntermediate> {
    val json = checkNotNull(javaClass.classLoader.getResourceAsStream(FIXTURE)) { "Missing $FIXTURE" }
      .bufferedReader()
      .use { it.readText() }

    return Json { ignoreUnknownKeys = true }.decodeFromString(json)
  }

  private fun readCertificate(resource: String): X509Certificate {
    return checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
      .use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
  }

  /** One entry of the upstream impacted-intermediates.json. Fields we do not need are ignored. */
  @Serializable
  private data class ImpactedIntermediate(
    val name: String,
    @SerialName("issuer_operator") val issuerOperator: String,
    @SerialName("spki_sha256") val spkiSha256: String,
    val pem: String
  ) {
    fun certificate(): X509Certificate {
      return CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
    }
  }

  /** Models a validating trust manager that caches intermediates from successful validations. */
  private class CachingTrustManager : X509TrustManager {
    var hasCachedIntermediate = false
      private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
      if (chain.size > 1) {
        hasCachedIntermediate = true
      } else if (!hasCachedIntermediate) {
        throw CertificateException("Missing intermediate")
      }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
  }

  companion object {
    /**
     * Verbatim copy of the impacted-intermediates.json that Mozilla attached to its guidance for trust store operators at
     * https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE (incident report:
     * https://bugzilla.mozilla.org/show_bug.cgi?id=2083449). Also mirrored at
     * https://emudhra.we-have-a-problem.com/impacted-intermediates.json
     */
    private const val FIXTURE = "emudhra-impacted-intermediates.json"
    private const val AUTH_TYPE = "ECDHE_ECDSA"
  }
}
