package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.mockk.mockk
import io.mockk.spyk
import okhttp3.OkHttpClient
import org.signal.core.util.UptimeSleepTimer
import org.signal.core.util.billing.BillingApi
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.network.api.ArchiveApi
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.testing.endpoints.DonationTestServer
import org.thoughtcrime.securesms.testing.endpoints.MockEndpoints
import org.thoughtcrime.securesms.testing.endpoints.ResponderInterceptor
import org.thoughtcrime.securesms.testing.endpoints.ResponderWebSocketConnection
import org.whispersystems.signalservice.api.SignalServiceDataStore
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

/**
 * Dependency provider used for instrumentation tests (aka androidTests).
 *
 * Handles setting up a mock web server for API calls, and provides mockable versions of [SignalServiceNetworkAccess].
 */
class InstrumentationApplicationDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {

  private val recipientCache: LiveRecipientCache
  private var signalServiceMessageSender: SignalServiceMessageSender? = null
  private var billingApi: BillingApi = mockk()
  private var accountApi: AccountApi = mockk()

  init {
    recipientCache = LiveRecipientCache(application) { r -> r.run() }
  }

  override fun provideBillingApi(): BillingApi = billingApi

  override fun provideAccountApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): AccountApi = accountApi

  override fun provideRecipientCache(): LiveRecipientCache {
    return recipientCache
  }

  override fun provideArchiveApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, signalServiceConfiguration: SignalServiceConfiguration): ArchiveApi {
    return mockk()
  }

  /**
   * Adds the Stripe-matching [ResponderInterceptor] on top of the default client (which supplies the
   * user agent + DNS), so `api.stripe.com` requests made by [org.signal.donations.StripeApi] are
   * answered from the shared [MockEndpoints.responder] and never hit the real network.
   */
  override fun provideOkHttpClient(): OkHttpClient {
    return default.provideOkHttpClient()
      .newBuilder()
      .addInterceptor(ResponderInterceptor(MockEndpoints.responder))
      .build()
  }

  /**
   * Backs the real [org.whispersystems.signalservice.api.donations.DonationsApi] (and any other
   * websocket API) with a [ResponderWebSocketConnection] driven by the shared [MockEndpoints.responder],
   * so Signal-service requests are answered from the scenario's "world" and never hit the network.
   */
  override fun provideAuthWebSocket(
    signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>,
    libSignalNetworkSupplier: Supplier<Network>
  ): SignalWebSocket.AuthenticatedWebSocket {
    return SignalWebSocket.AuthenticatedWebSocket(
      connectionFactory = { ResponderWebSocketConnection(MockEndpoints.responder) },
      canConnect = { true },
      sleepTimer = UptimeSleepTimer(),
      disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
    )
  }

  override fun provideUnauthWebSocket(
    signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>,
    libSignalNetworkSupplier: Supplier<Network>
  ): SignalWebSocket.UnauthenticatedWebSocket {
    return SignalWebSocket.UnauthenticatedWebSocket(
      connectionFactory = { ResponderWebSocketConnection(MockEndpoints.responder) },
      canConnect = { true },
      sleepTimer = UptimeSleepTimer(),
      disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
    )
  }

  /**
   * Uses the test zk server's public params so credentials minted by [MockEndpoints] validate here.
   * The real [ClientZkReceiptOperations.receiveReceiptCredential] validation still runs in the
   * receipt-credential context job — only the params are swapped for test ones.
   */
  override fun provideClientZkReceiptOperations(signalServiceConfiguration: SignalServiceConfiguration): ClientZkReceiptOperations {
    return DonationTestServer.clientReceiptOperations
  }

  override fun provideSignalServiceMessageSender(
    protocolStore: SignalServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): SignalServiceMessageSender {
    if (signalServiceMessageSender == null) {
      signalServiceMessageSender = spyk(objToCopy = default.provideSignalServiceMessageSender(protocolStore, pushServiceSocket, messageApi, keysApi))
    }
    return signalServiceMessageSender!!
  }
}
