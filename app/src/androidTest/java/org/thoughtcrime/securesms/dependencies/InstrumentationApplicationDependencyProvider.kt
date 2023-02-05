package org.thoughtcrime.securesms.dependencies

import android.app.Application
import okhttp3.ConnectionSpec
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.KbsEnclave
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.push.SignalServiceTrustStore
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.testing.Verb
import org.thoughtcrime.securesms.testing.runSync
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.api.KeyBackupService
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import java.security.KeyStore
import java.util.Optional

/**
 * Dependency provider used for instrumentation tests (aka androidTests).
 *
 * Handles setting up a mock web server for API calls, and provides mockable versions of [SignalServiceNetworkAccess] and
 * [KeyBackupService].
 */
class InstrumentationApplicationDependencyProvider(application: Application, default: ApplicationDependencyProvider) : ApplicationDependencies.Provider by default {

  private val serviceTrustStore: TrustStore
  private val uncensoredConfiguration: SignalServiceConfiguration
  private val serviceNetworkAccessMock: SignalServiceNetworkAccess
  private val keyBackupService: KeyBackupService
  private val recipientCache: LiveRecipientCache

  init {
    runSync {
      webServer = MockWebServer()
      baseUrl = webServer.url("").toString()
    }

    webServer.setDispatcher(object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val handler = handlers.firstOrNull {
          request.method == it.verb && request.path.startsWith("/${it.path}")
        }
        return handler?.responseFactory?.invoke(request) ?: MockResponse().setResponseCode(500)
      }
    })

    serviceTrustStore = SignalServiceTrustStore(application)
    uncensoredConfiguration = SignalServiceConfiguration(
      arrayOf(SignalServiceUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      mapOf(
        0 to arrayOf(SignalCdnUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
        2 to arrayOf(SignalCdnUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT))
      ),
      arrayOf(SignalContactDiscoveryUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      arrayOf(SignalKeyBackupServiceUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      arrayOf(SignalStorageUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      arrayOf(SignalCdsiUrl(baseUrl, "localhost", serviceTrustStore, ConnectionSpec.CLEARTEXT)),
      emptyList(),
      Optional.of(SignalServiceNetworkAccess.DNS),
      Optional.empty(),
      Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS),
      true
    )

    serviceNetworkAccessMock = mock {
      on { getConfiguration() } doReturn uncensoredConfiguration
      on { getConfiguration(any()) } doReturn uncensoredConfiguration
      on { uncensoredConfiguration } doReturn uncensoredConfiguration
    }

    keyBackupService = mock()

    recipientCache = LiveRecipientCache(application) { r -> r.run() }
  }

  override fun provideSignalServiceNetworkAccess(): SignalServiceNetworkAccess {
    return serviceNetworkAccessMock
  }

  override fun provideKeyBackupService(signalServiceAccountManager: SignalServiceAccountManager, keyStore: KeyStore, enclave: KbsEnclave): KeyBackupService {
    return keyBackupService
  }

  override fun provideRecipientCache(): LiveRecipientCache {
    return recipientCache
  }

  companion object {
    lateinit var webServer: MockWebServer
      private set
    lateinit var baseUrl: String
      private set

    private val handlers: MutableList<Verb> = mutableListOf()

    fun addMockWebRequestHandlers(vararg verbs: Verb) {
      handlers.addAll(verbs)
    }

    fun clearHandlers() {
      handlers.clear()
    }
  }
}
