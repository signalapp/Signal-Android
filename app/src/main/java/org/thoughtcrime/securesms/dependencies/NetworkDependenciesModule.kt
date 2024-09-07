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
import org.whispersystems.signalservice.api.SignalWebSocket
import org.whispersystems.signalservice.api.archive.ArchiveApi
import org.whispersystems.signalservice.api.attachment.AttachmentApi
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.services.CallLinksService
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
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

  private val disposables: CompositeDisposable = CompositeDisposable()

  val signalServiceNetworkAccess: SignalServiceNetworkAccess by lazy {
    provider.provideSignalServiceNetworkAccess()
  }

  private val _protocolStore = resettableLazy {
    provider.provideProtocolStore()
  }
  val protocolStore: SignalServiceDataStoreImpl by _protocolStore

  private val _signalServiceMessageSender = resettableLazy {
    provider.provideSignalServiceMessageSender(signalWebSocket, protocolStore, pushServiceSocket)
  }
  val signalServiceMessageSender: SignalServiceMessageSender by _signalServiceMessageSender

  val incomingMessageObserver: IncomingMessageObserver by lazy {
    provider.provideIncomingMessageObserver()
  }

  val pushServiceSocket: PushServiceSocket by lazy {
    provider.providePushServiceSocket(signalServiceNetworkAccess.getConfiguration(), groupsV2Operations)
  }

  val signalServiceAccountManager: SignalServiceAccountManager by lazy {
    provider.provideSignalServiceAccountManager(pushServiceSocket, groupsV2Operations)
  }

  val libsignalNetwork: Network by lazy {
    provider.provideLibsignalNetwork(signalServiceNetworkAccess.getConfiguration())
  }

  val signalWebSocket: SignalWebSocket by lazy {
    provider.provideSignalWebSocket({ signalServiceNetworkAccess.getConfiguration() }, { libsignalNetwork }).also {
      disposables += it.webSocketState.subscribe { webSocketStateSubject.onNext(it) }
    }
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
    provider.providePayments(signalServiceAccountManager)
  }

  val callLinksService: CallLinksService by lazy {
    provider.provideCallLinksService(pushServiceSocket)
  }

  val profileService: ProfileService by lazy {
    provider.provideProfileService(groupsV2Operations.profileOperations, signalServiceMessageReceiver, signalWebSocket)
  }

  val donationsService: DonationsService by lazy {
    provider.provideDonationsService(pushServiceSocket)
  }

  val archiveApi: ArchiveApi by lazy {
    provider.provideArchiveApi(pushServiceSocket)
  }

  val keysApi: KeysApi by lazy {
    provider.provideKeysApi(pushServiceSocket)
  }

  val attachmentApi: AttachmentApi by lazy {
    provider.provideAttachmentApi(signalWebSocket, pushServiceSocket)
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
    incomingMessageObserver.terminateAsync()
    if (_signalServiceMessageSender.isInitialized()) {
      signalServiceMessageSender.cancelInFlightRequests()
    }
    disposables.clear()
  }

  fun resetProtocolStores() {
    _protocolStore.reset()
    _signalServiceMessageSender.reset()
  }
}
