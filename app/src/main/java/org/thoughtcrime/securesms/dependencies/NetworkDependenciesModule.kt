/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.app.Application
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.Subject
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.signal.core.util.logging.Log
import org.signal.core.util.resettableLazy
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.thoughtcrime.securesms.crypto.storage.SignalServiceDataStoreImpl
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.groups.GroupsV2AuthorizationMemoryValueCache
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor
import org.thoughtcrime.securesms.payments.Payments
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.push.SignalServiceTrustStore
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.archive.ArchiveApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.calling.CallingApi
import org.whispersystems.signalservice.api.cds.CdsApi
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.link.LinkDeviceApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.payments.PaymentsApi
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.signalservice.api.registration.RegistrationApi
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.api.username.UsernameApi
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager
import org.whispersystems.signalservice.internal.util.Util
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * A subset of [AppDependencies] that relies on the network. We bundle them together because when the network
 * needs to get reset, we just throw out the whole thing and recreate it.
 */
class NetworkDependenciesModule(
  private val application: Application,
  private val provider: AppDependencies.Provider,
  private val webSocketStateSubject: Subject<WebSocketConnectionState>
) {

  companion object {
    private val TAG = "NetworkDependencies"
  }

  private val disposables: CompositeDisposable = CompositeDisposable()

  val signalServiceNetworkAccess: SignalServiceNetworkAccess by lazy {
    provider.provideSignalServiceNetworkAccess()
  }

  private val _protocolStore = resettableLazy {
    provider.provideProtocolStore()
  }
  val protocolStore: SignalServiceDataStoreImpl by _protocolStore

  private val _signalServiceMessageSender = resettableLazy {
    provider.provideSignalServiceMessageSender(authWebSocket, unauthWebSocket, protocolStore, pushServiceSocket)
  }
  val signalServiceMessageSender: SignalServiceMessageSender by _signalServiceMessageSender

  val incomingMessageObserver: IncomingMessageObserver by lazy {
    provider.provideIncomingMessageObserver(authWebSocket)
  }

  val pushServiceSocket: PushServiceSocket by lazy {
    provider.providePushServiceSocket(signalServiceNetworkAccess.getConfiguration(), groupsV2Operations)
  }

  val signalServiceAccountManager: SignalServiceAccountManager by lazy {
    provider.provideSignalServiceAccountManager(accountApi, pushServiceSocket, groupsV2Operations)
  }

  val libsignalNetwork: Network by lazy {
    provider.provideLibsignalNetwork(signalServiceNetworkAccess.getConfiguration())
  }

  val authWebSocket: SignalWebSocket.AuthenticatedWebSocket by lazy {
    provider.provideAuthWebSocket({ signalServiceNetworkAccess.getConfiguration() }, { libsignalNetwork }).also {
      disposables += it.state.subscribe { s -> webSocketStateSubject.onNext(s) }
    }
  }

  val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket by lazy {
    provider.provideUnauthWebSocket({ signalServiceNetworkAccess.getConfiguration() }, { libsignalNetwork })
  }

  val groupsV2Authorization: GroupsV2Authorization by lazy {
    val authCache: GroupsV2Authorization.ValueCache = GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AciAuthorizationCache)
    GroupsV2Authorization(signalServiceAccountManager.groupsV2Api, authCache)
  }

  val groupsV2Operations: GroupsV2Operations by lazy {
    provider.provideGroupsV2Operations(signalServiceNetworkAccess.getConfiguration())
  }

  val clientZkReceiptOperations: ClientZkReceiptOperations by lazy {
    provider.provideClientZkReceiptOperations(signalServiceNetworkAccess.getConfiguration())
  }

  val signalServiceMessageReceiver: SignalServiceMessageReceiver by lazy {
    provider.provideSignalServiceMessageReceiver(pushServiceSocket)
  }

  val payments: Payments by lazy {
    provider.providePayments(paymentsApi)
  }

  val profileService: ProfileService by lazy {
    provider.provideProfileService(groupsV2Operations.profileOperations, signalServiceMessageReceiver, authWebSocket, unauthWebSocket)
  }

  val donationsService: DonationsService by lazy {
    provider.provideDonationsService(pushServiceSocket)
  }

  val archiveApi: ArchiveApi by lazy {
    provider.provideArchiveApi(authWebSocket, unauthWebSocket, pushServiceSocket)
  }

  val keysApi: KeysApi by lazy {
    provider.provideKeysApi(pushServiceSocket)
  }

  val attachmentApi: AttachmentApi by lazy {
    provider.provideAttachmentApi(authWebSocket, pushServiceSocket)
  }

  val linkDeviceApi: LinkDeviceApi by lazy {
    provider.provideLinkDeviceApi(authWebSocket)
  }

  val registrationApi: RegistrationApi by lazy {
    provider.provideRegistrationApi(pushServiceSocket)
  }

  val storageServiceApi: StorageServiceApi by lazy {
    provider.provideStorageServiceApi(authWebSocket, pushServiceSocket)
  }

  val accountApi: AccountApi by lazy {
    provider.provideAccountApi(authWebSocket)
  }

  val usernameApi: UsernameApi by lazy {
    provider.provideUsernameApi(unauthWebSocket)
  }

  val callingApi: CallingApi by lazy {
    provider.provideCallingApi(authWebSocket, pushServiceSocket)
  }

  val paymentsApi: PaymentsApi by lazy {
    provider.providePaymentsApi(authWebSocket)
  }

  val cdsApi: CdsApi by lazy {
    provider.provideCdsApi(authWebSocket)
  }

  val rateLimitChallengeApi: RateLimitChallengeApi by lazy {
    provider.provideRateLimitChallengeApi(authWebSocket)
  }

  val messageApi: MessageApi by lazy {
    provider.provideMessageApi(authWebSocket, unauthWebSocket)
  }

  val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .addInterceptor(StandardUserAgentInterceptor())
      .dns(SignalServiceNetworkAccess.DNS)
      .build()
  }

  val signalOkHttpClient: OkHttpClient by lazy {
    try {
      val baseClient = okHttpClient
      val sslContext = SSLContext.getInstance("TLS")
      val trustStore: TrustStore = SignalServiceTrustStore(application)
      val trustManagers = BlacklistingTrustManager.createFor(trustStore)

      sslContext.init(null, trustManagers, null)

      baseClient.newBuilder()
        .sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManagers[0] as X509TrustManager)
        .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
        .build()
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: KeyManagementException) {
      throw AssertionError(e)
    }
  }

  fun closeConnections() {
    Log.i(TAG, "Closing connections.")
    incomingMessageObserver.terminateAsync()
    if (_signalServiceMessageSender.isInitialized()) {
      signalServiceMessageSender.cancelInFlightRequests()
    }
    unauthWebSocket.disconnect()
    disposables.clear()
  }

  fun openConnections() {
    try {
      authWebSocket.connect()
    } catch (e: WebSocketUnavailableException) {
      Log.w(TAG, "Not allowed to start auth websocket", e)
    }

    try {
      unauthWebSocket.connect()
    } catch (e: WebSocketUnavailableException) {
      Log.w(TAG, "Not allowed to start unauth websocket", e)
    }

    incomingMessageObserver
  }

  fun resetProtocolStores() {
    _protocolStore.reset()
    _signalServiceMessageSender.reset()
  }
}
