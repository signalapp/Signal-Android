package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.mockk.mockk
import io.mockk.spyk
import org.signal.core.util.billing.BillingApi
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.whispersystems.signalservice.api.SignalServiceDataStore
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.archive.ArchiveApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.donations.DonationsApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.PushServiceSocket

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

  override fun provideArchiveApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi {
    return mockk()
  }

  override fun provideDonationsApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket): DonationsApi {
    return mockk()
  }

  override fun provideSignalServiceMessageSender(
    protocolStore: SignalServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    attachmentApi: AttachmentApi,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): SignalServiceMessageSender {
    if (signalServiceMessageSender == null) {
      signalServiceMessageSender = spyk(objToCopy = default.provideSignalServiceMessageSender(protocolStore, pushServiceSocket, attachmentApi, messageApi, keysApi))
    }
    return signalServiceMessageSender!!
  }
}
