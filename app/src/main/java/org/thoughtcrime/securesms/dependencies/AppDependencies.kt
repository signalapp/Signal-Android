package org.thoughtcrime.securesms.dependencies

import android.annotation.SuppressLint
import android.app.Application
import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import okhttp3.OkHttpClient
import org.signal.core.util.concurrent.DeadlockDetector
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
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.thoughtcrime.securesms.util.EarlyMessageCache
import org.thoughtcrime.securesms.util.FrameRateTracker
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.SignalServiceDataStore
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.SignalWebSocket
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.services.CallLinksService
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
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

  // Needs special initialization because it needs to be created on the main thread
  private lateinit var _appForegroundObserver: AppForegroundObserver

  @JvmStatic
  @MainThread
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::provider.isInitialized) {
      throw IllegalStateException("Already initialized!")
    }

    _application = application
    AppDependencies.provider = provider

    _appForegroundObserver = provider.provideAppForegroundObserver()
    _appForegroundObserver.begin()
  }

  @JvmStatic
  val isInitialized: Boolean
    get() = this::_application.isInitialized

  @JvmStatic
  val application: Application
    get() = _application

  @JvmStatic
  val appForegroundObserver: AppForegroundObserver
    get() = _appForegroundObserver

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

  private val _webSocketObserver: Subject<WebSocketConnectionState> = BehaviorSubject.create()

  /**
   * An observable that emits the current state of the WebSocket connection across the various lifecycles
   * of the [signalWebSocket].
   */
  @JvmStatic
  val webSocketObserver: Observable<WebSocketConnectionState> = _webSocketObserver

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
  val signalWebSocket: SignalWebSocket
    get() = networkModule.signalWebSocket

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
  val callLinksService: CallLinksService
    get() = networkModule.callLinksService

  @JvmStatic
  val profileService: ProfileService
    get() = networkModule.profileService

  @JvmStatic
  val donationsService: DonationsService
    get() = networkModule.donationsService

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

  interface Provider {
    fun provideGroupsV2Operations(signalServiceConfiguration: SignalServiceConfiguration): GroupsV2Operations
    fun provideSignalServiceAccountManager(signalServiceConfiguration: SignalServiceConfiguration, groupsV2Operations: GroupsV2Operations): SignalServiceAccountManager
    fun provideSignalServiceMessageSender(signalWebSocket: SignalWebSocket, protocolStore: SignalServiceDataStore, signalServiceConfiguration: SignalServiceConfiguration): SignalServiceMessageSender
    fun provideSignalServiceMessageReceiver(signalServiceConfiguration: SignalServiceConfiguration): SignalServiceMessageReceiver
    fun provideSignalServiceNetworkAccess(): SignalServiceNetworkAccess
    fun provideRecipientCache(): LiveRecipientCache
    fun provideJobManager(): JobManager
    fun provideFrameRateTracker(): FrameRateTracker
    fun provideMegaphoneRepository(): MegaphoneRepository
    fun provideEarlyMessageCache(): EarlyMessageCache
    fun provideMessageNotifier(): MessageNotifier
    fun provideIncomingMessageObserver(): IncomingMessageObserver
    fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager
    fun provideViewOnceMessageManager(): ViewOnceMessageManager
    fun provideExpiringStoriesManager(): ExpiringStoriesManager
    fun provideExpiringMessageManager(): ExpiringMessageManager
    fun provideDeletedCallEventManager(): DeletedCallEventManager
    fun provideTypingStatusRepository(): TypingStatusRepository
    fun provideTypingStatusSender(): TypingStatusSender
    fun provideDatabaseObserver(): DatabaseObserver
    fun providePayments(signalServiceAccountManager: SignalServiceAccountManager): Payments
    fun provideShakeToReport(): ShakeToReport
    fun provideAppForegroundObserver(): AppForegroundObserver
    fun provideSignalCallManager(): SignalCallManager
    fun providePendingRetryReceiptManager(): PendingRetryReceiptManager
    fun providePendingRetryReceiptCache(): PendingRetryReceiptCache
    fun provideSignalWebSocket(signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>, libSignalNetworkSupplier: Supplier<Network>): SignalWebSocket
    fun provideProtocolStore(): SignalServiceDataStoreImpl
    fun provideGiphyMp4Cache(): GiphyMp4Cache
    fun provideExoPlayerPool(): SimpleExoPlayerPool
    fun provideAndroidCallAudioManager(): AudioManagerCompat
    fun provideDonationsService(signalServiceConfiguration: SignalServiceConfiguration, groupsV2Operations: GroupsV2Operations): DonationsService
    fun provideCallLinksService(signalServiceConfiguration: SignalServiceConfiguration, groupsV2Operations: GroupsV2Operations): CallLinksService
    fun provideProfileService(profileOperations: ClientZkProfileOperations, signalServiceMessageReceiver: SignalServiceMessageReceiver, signalWebSocket: SignalWebSocket): ProfileService
    fun provideDeadlockDetector(): DeadlockDetector
    fun provideClientZkReceiptOperations(signalServiceConfiguration: SignalServiceConfiguration): ClientZkReceiptOperations
    fun provideScheduledMessageManager(): ScheduledMessageManager
    fun provideLibsignalNetwork(config: SignalServiceConfiguration): Network
  }
}
