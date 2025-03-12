package org.thoughtcrime.securesms.dependencies

import android.annotation.SuppressLint
import android.app.Application
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.concurrent.DeadlockDetector
import org.signal.core.util.concurrent.LatestValueObservable
import org.signal.core.util.resettableLazy
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.thoughtcrime.securesms.components.TypingStatusRepository
import org.thoughtcrime.securesms.components.TypingStatusSender
import org.thoughtcrime.securesms.crypto.storage.SignalServiceDataStoreImpl
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.notifications.MessageNotifier
import org.thoughtcrime.securesms.payments.Payments
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.revealable.ViewOnceMessageManager
import org.thoughtcrime.securesms.service.DeletedCallEventManager
import org.thoughtcrime.securesms.service.ExpiringMessageManager
import org.thoughtcrime.securesms.service.ExpiringStoriesManager
import org.thoughtcrime.securesms.service.PendingRetryReceiptManager
import org.thoughtcrime.securesms.service.ScheduledMessageManager
import org.thoughtcrime.securesms.service.TrimThreadsByDateManager
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager
import org.thoughtcrime.securesms.shakereport.ShakeToReport
import org.thoughtcrime.securesms.util.EarlyMessageCache
import org.thoughtcrime.securesms.util.FrameRateTracker
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.SignalServiceDataStore
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
import org.whispersystems.signalservice.api.ratelimit.RateLimitChallengeApi
import org.whispersystems.signalservice.api.registration.RegistrationApi
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.api.username.UsernameApi
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.function.Supplier

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * [.init] before using any of the methods, preferably early on in
 * [Application.onCreate].
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
@SuppressLint("StaticFieldLeak")
object AppDependencies {
  private lateinit var _application: Application
  private lateinit var provider: Provider

  @JvmStatic
  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::provider.isInitialized) {
      return
    }

    _application = application
    AppDependencies.provider = provider
  }

  @JvmStatic
  val isInitialized: Boolean
    get() = this::_application.isInitialized

  @JvmStatic
  val application: Application
    get() = _application

  @JvmStatic
  val recipientCache: LiveRecipientCache by lazy {
    provider.provideRecipientCache()
  }

  @JvmStatic
  val jobManager: JobManager by lazy {
    provider.provideJobManager()
  }

  @JvmStatic
  val frameRateTracker: FrameRateTracker by lazy {
    provider.provideFrameRateTracker()
  }

  @JvmStatic
  val megaphoneRepository: MegaphoneRepository by lazy {
    provider.provideMegaphoneRepository()
  }

  @JvmStatic
  val earlyMessageCache: EarlyMessageCache by lazy {
    provider.provideEarlyMessageCache()
  }

  @JvmStatic
  val typingStatusRepository: TypingStatusRepository by lazy {
    provider.provideTypingStatusRepository()
  }

  @JvmStatic
  val typingStatusSender: TypingStatusSender by lazy {
    provider.provideTypingStatusSender()
  }

  @JvmStatic
  val databaseObserver: DatabaseObserver by lazy {
    provider.provideDatabaseObserver()
  }

  @JvmStatic
  val trimThreadsByDateManager: TrimThreadsByDateManager by lazy {
    provider.provideTrimThreadsByDateManager()
  }

  @JvmStatic
  val viewOnceMessageManager: ViewOnceMessageManager by lazy {
    provider.provideViewOnceMessageManager()
  }

  @JvmStatic
  val expiringMessageManager: ExpiringMessageManager by lazy {
    provider.provideExpiringMessageManager()
  }

  @JvmStatic
  val deletedCallEventManager: DeletedCallEventManager by lazy {
    provider.provideDeletedCallEventManager()
  }

  @JvmStatic
  val signalCallManager: SignalCallManager by lazy {
    provider.provideSignalCallManager()
  }

  @JvmStatic
  val shakeToReport: ShakeToReport by lazy {
    provider.provideShakeToReport()
  }

  @JvmStatic
  val pendingRetryReceiptManager: PendingRetryReceiptManager by lazy {
    provider.providePendingRetryReceiptManager()
  }

  @JvmStatic
  val pendingRetryReceiptCache: PendingRetryReceiptCache by lazy {
    provider.providePendingRetryReceiptCache()
  }

  @JvmStatic
  val messageNotifier: MessageNotifier by lazy {
    provider.provideMessageNotifier()
  }

  @JvmStatic
  val giphyMp4Cache: GiphyMp4Cache by lazy {
    provider.provideGiphyMp4Cache()
  }

  @JvmStatic
  val exoPlayerPool: SimpleExoPlayerPool by lazy {
    provider.provideExoPlayerPool()
  }

  @JvmStatic
  val deadlockDetector: DeadlockDetector by lazy {
    provider.provideDeadlockDetector()
  }

  @JvmStatic
  val expireStoriesManager: ExpiringStoriesManager by lazy {
    provider.provideExpiringStoriesManager()
  }

  @JvmStatic
  val scheduledMessageManager: ScheduledMessageManager by lazy {
    provider.provideScheduledMessageManager()
  }

  @JvmStatic
  val androidCallAudioManager: AudioManagerCompat by lazy {
    provider.provideAndroidCallAudioManager()
  }

  @JvmStatic
  val billingApi: BillingApi by lazy {
    provider.provideBillingApi()
  }

  private val _webSocketObserver: BehaviorSubject<WebSocketConnectionState> = BehaviorSubject.create()

  /**
   * An observable that emits the current state of the WebSocket connection across the various lifecycles
   * of the [authWebSocket].
   */
  @JvmStatic
  val webSocketObserver: LatestValueObservable<WebSocketConnectionState> = LatestValueObservable(_webSocketObserver)

  private val _networkModule = resettableLazy {
    NetworkDependenciesModule(application, provider, _webSocketObserver)
  }
  private val networkModule by _networkModule

  @JvmStatic
  val signalServiceNetworkAccess: SignalServiceNetworkAccess
    get() = networkModule.signalServiceNetworkAccess

  @JvmStatic
  val protocolStore: SignalServiceDataStoreImpl
    get() = networkModule.protocolStore

  @JvmStatic
  val signalServiceMessageSender: SignalServiceMessageSender
    get() = networkModule.signalServiceMessageSender

  @JvmStatic
  val signalServiceAccountManager: SignalServiceAccountManager
    get() = networkModule.signalServiceAccountManager

  @JvmStatic
  val signalServiceMessageReceiver: SignalServiceMessageReceiver
    get() = networkModule.signalServiceMessageReceiver

  @JvmStatic
  val incomingMessageObserver: IncomingMessageObserver
    get() = networkModule.incomingMessageObserver

  @JvmStatic
  val libsignalNetwork: Network
    get() = networkModule.libsignalNetwork

  @JvmStatic
  val authWebSocket: SignalWebSocket.AuthenticatedWebSocket
    get() = networkModule.authWebSocket

  @JvmStatic
  val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
    get() = networkModule.unauthWebSocket

  @JvmStatic
  val groupsV2Authorization: GroupsV2Authorization
    get() = networkModule.groupsV2Authorization

  @JvmStatic
  val groupsV2Operations: GroupsV2Operations
    get() = networkModule.groupsV2Operations

  @JvmStatic
  val clientZkReceiptOperations
    get() = networkModule.clientZkReceiptOperations

  @JvmStatic
  val payments: Payments
    get() = networkModule.payments

  @JvmStatic
  val profileService: ProfileService
    get() = networkModule.profileService

  @JvmStatic
  val donationsService: DonationsService
    get() = networkModule.donationsService

  @JvmStatic
  val archiveApi: ArchiveApi
    get() = networkModule.archiveApi

  @JvmStatic
  val keysApi: KeysApi
    get() = networkModule.keysApi

  @JvmStatic
  val attachmentApi: AttachmentApi
    get() = networkModule.attachmentApi

  @JvmStatic
  val linkDeviceApi: LinkDeviceApi
    get() = networkModule.linkDeviceApi

  @JvmStatic
  val registrationApi: RegistrationApi
    get() = networkModule.registrationApi

  val storageServiceApi: StorageServiceApi
    get() = networkModule.storageServiceApi

  val accountApi: AccountApi
    get() = networkModule.accountApi

  val usernameApi: UsernameApi
    get() = networkModule.usernameApi

  val callingApi: CallingApi
    get() = networkModule.callingApi

  val paymentsApi: PaymentsApi
    get() = networkModule.paymentsApi

  val cdsApi: CdsApi
    get() = networkModule.cdsApi

  val rateLimitChallengeApi: RateLimitChallengeApi
    get() = networkModule.rateLimitChallengeApi

  val messageApi: MessageApi
    get() = networkModule.messageApi

  @JvmStatic
  val okHttpClient: OkHttpClient
    get() = networkModule.okHttpClient

  @JvmStatic
  val signalOkHttpClient: OkHttpClient
    get() = networkModule.signalOkHttpClient

  @JvmStatic
  fun resetProtocolStores() {
    networkModule.resetProtocolStores()
  }

  @JvmStatic
  fun resetNetwork() {
    networkModule.closeConnections()
    _networkModule.reset()
  }

  @JvmStatic
  fun startNetwork() {
    networkModule.openConnections()
  }

  interface Provider {
    fun providePushServiceSocket(signalServiceConfiguration: SignalServiceConfiguration, groupsV2Operations: GroupsV2Operations): PushServiceSocket
    fun provideGroupsV2Operations(signalServiceConfiguration: SignalServiceConfiguration): GroupsV2Operations
    fun provideSignalServiceAccountManager(authWebSocket: AccountApi, pushServiceSocket: PushServiceSocket, groupsV2Operations: GroupsV2Operations): SignalServiceAccountManager
    fun provideSignalServiceMessageSender(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket, protocolStore: SignalServiceDataStore, pushServiceSocket: PushServiceSocket): SignalServiceMessageSender
    fun provideSignalServiceMessageReceiver(pushServiceSocket: PushServiceSocket): SignalServiceMessageReceiver
    fun provideSignalServiceNetworkAccess(): SignalServiceNetworkAccess
    fun provideRecipientCache(): LiveRecipientCache
    fun provideJobManager(): JobManager
    fun provideFrameRateTracker(): FrameRateTracker
    fun provideMegaphoneRepository(): MegaphoneRepository
    fun provideEarlyMessageCache(): EarlyMessageCache
    fun provideMessageNotifier(): MessageNotifier
    fun provideIncomingMessageObserver(webSocket: SignalWebSocket.AuthenticatedWebSocket): IncomingMessageObserver
    fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager
    fun provideViewOnceMessageManager(): ViewOnceMessageManager
    fun provideExpiringStoriesManager(): ExpiringStoriesManager
    fun provideExpiringMessageManager(): ExpiringMessageManager
    fun provideDeletedCallEventManager(): DeletedCallEventManager
    fun provideTypingStatusRepository(): TypingStatusRepository
    fun provideTypingStatusSender(): TypingStatusSender
    fun provideDatabaseObserver(): DatabaseObserver
    fun providePayments(paymentsApi: PaymentsApi): Payments
    fun provideShakeToReport(): ShakeToReport
    fun provideSignalCallManager(): SignalCallManager
    fun providePendingRetryReceiptManager(): PendingRetryReceiptManager
    fun providePendingRetryReceiptCache(): PendingRetryReceiptCache
    fun provideProtocolStore(): SignalServiceDataStoreImpl
    fun provideGiphyMp4Cache(): GiphyMp4Cache
    fun provideExoPlayerPool(): SimpleExoPlayerPool
    fun provideAndroidCallAudioManager(): AudioManagerCompat
    fun provideDonationsService(pushServiceSocket: PushServiceSocket): DonationsService
    fun provideProfileService(profileOperations: ClientZkProfileOperations, signalServiceMessageReceiver: SignalServiceMessageReceiver, authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket): ProfileService
    fun provideDeadlockDetector(): DeadlockDetector
    fun provideClientZkReceiptOperations(signalServiceConfiguration: SignalServiceConfiguration): ClientZkReceiptOperations
    fun provideScheduledMessageManager(): ScheduledMessageManager
    fun provideLibsignalNetwork(config: SignalServiceConfiguration): Network
    fun provideBillingApi(): BillingApi
    fun provideArchiveApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): ArchiveApi
    fun provideKeysApi(pushServiceSocket: PushServiceSocket): KeysApi
    fun provideAttachmentApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): AttachmentApi
    fun provideLinkDeviceApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): LinkDeviceApi
    fun provideRegistrationApi(pushServiceSocket: PushServiceSocket): RegistrationApi
    fun provideStorageServiceApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): StorageServiceApi
    fun provideAuthWebSocket(signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>, libSignalNetworkSupplier: Supplier<Network>): SignalWebSocket.AuthenticatedWebSocket
    fun provideUnauthWebSocket(signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>, libSignalNetworkSupplier: Supplier<Network>): SignalWebSocket.UnauthenticatedWebSocket
    fun provideAccountApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): AccountApi
    fun provideUsernameApi(unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket): UsernameApi
    fun provideCallingApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): CallingApi
    fun providePaymentsApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): PaymentsApi
    fun provideCdsApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): CdsApi
    fun provideRateLimitChallengeApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket): RateLimitChallengeApi
    fun provideMessageApi(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket): MessageApi
  }
}
