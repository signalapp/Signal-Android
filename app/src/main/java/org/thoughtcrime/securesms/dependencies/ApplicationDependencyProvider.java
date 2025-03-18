package org.thoughtcrime.securesms.dependencies;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.billing.BillingFactory;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.billing.BillingApi;
import org.signal.core.util.concurrent.DeadlockDetector;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.storage.SignalBaseIdentityKeyStore;
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore;
import org.thoughtcrime.securesms.crypto.storage.SignalKyberPreKeyStore;
import org.thoughtcrime.securesms.crypto.storage.SignalSenderKeyStore;
import org.thoughtcrime.securesms.crypto.storage.SignalServiceAccountDataStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.SignalServiceDataStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobMigrator;
import org.thoughtcrime.securesms.jobmanager.impl.FactoryJobPredicate;
import org.thoughtcrime.securesms.jobs.FastJobStorage;
import org.thoughtcrime.securesms.jobs.GroupCallUpdateSendJob;
import org.thoughtcrime.securesms.jobs.IndividualSendJob;
import org.thoughtcrime.securesms.jobs.JobManagerFactories;
import org.thoughtcrime.securesms.jobs.MarkerJob;
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.thoughtcrime.securesms.jobs.ReactionSendJob;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.thoughtcrime.securesms.net.SignalWebSocketHealthMonitor;
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.OptimizedMessageNotifier;
import org.thoughtcrime.securesms.payments.MobileCoinConfig;
import org.thoughtcrime.securesms.payments.Payments;
import org.thoughtcrime.securesms.push.SecurityEventListener;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.LiveRecipientCache;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageManager;
import org.thoughtcrime.securesms.service.DeletedCallEventManager;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.ExpiringStoriesManager;
import org.thoughtcrime.securesms.service.PendingRetryReceiptManager;
import org.thoughtcrime.securesms.service.ScheduledMessageManager;
import org.thoughtcrime.securesms.service.TrimThreadsByDateManager;
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager;
import org.thoughtcrime.securesms.shakereport.ShakeToReport;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.AlarmSleepTimer;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.EarlyMessageCache;
import org.thoughtcrime.securesms.util.Environment;
import org.thoughtcrime.securesms.util.FrameRateTracker;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache;
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.account.AccountApi;
import org.whispersystems.signalservice.api.archive.ArchiveApi;
import org.whispersystems.signalservice.api.attachment.AttachmentApi;
import org.whispersystems.signalservice.api.calling.CallingApi;
import org.whispersystems.signalservice.api.cds.CdsApi;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.keys.KeysApi;
import org.whispersystems.signalservice.api.link.LinkDeviceApi;
import org.whispersystems.signalservice.api.message.MessageApi;
import org.whispersystems.signalservice.api.payments.PaymentsApi;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.ratelimit.RateLimitChallengeApi;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.services.DonationsService;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.storage.StorageServiceApi;
import org.whispersystems.signalservice.api.username.UsernameApi;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.websocket.LibSignalChatConnection;
import org.whispersystems.signalservice.internal.websocket.LibSignalNetworkExtensions;
import org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of {@link AppDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements AppDependencies.Provider {

  private final Application context;

  public ApplicationDependencyProvider(@NonNull Application context) {
    this.context = context;
  }

  private @NonNull ClientZkOperations provideClientZkOperations(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return ClientZkOperations.create(signalServiceConfiguration);
  }

  @Override
  public @NonNull PushServiceSocket providePushServiceSocket(@NonNull SignalServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations) {
    return new PushServiceSocket(signalServiceConfiguration,
                                 new DynamicCredentialsProvider(),
                                 BuildConfig.SIGNAL_AGENT,
                                 groupsV2Operations.getProfileOperations(),
                                 RemoteConfig.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return new GroupsV2Operations(provideClientZkOperations(signalServiceConfiguration), RemoteConfig.groupLimits().getHardLimit());
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager(@NonNull AccountApi accountApi, @NonNull PushServiceSocket pushServiceSocket, @NonNull GroupsV2Operations groupsV2Operations) {
    return new SignalServiceAccountManager(accountApi, pushServiceSocket, groupsV2Operations);
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull SignalServiceDataStore protocolStore, @NonNull PushServiceSocket pushServiceSocket) {
      return new SignalServiceMessageSender(pushServiceSocket,
                                            protocolStore,
                                            ReentrantSessionLock.INSTANCE,
                                            authWebSocket,
                                            unauthWebSocket,
                                            Optional.of(new SecurityEventListener(context)),
                                            SignalExecutors.newCachedBoundedExecutor("signal-messages", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD, 1, 16, 30),
                                            ByteUnit.KILOBYTES.toBytes(256));
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver(@NonNull PushServiceSocket pushServiceSocket) {
    return new SignalServiceMessageReceiver(pushServiceSocket);
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return new SignalServiceNetworkAccess(context);
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    JobManager.Configuration config = new JobManager.Configuration.Builder()
                                                                  .setJobFactories(JobManagerFactories.getJobFactories(context))
                                                                  .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                                                                  .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                                                                  .setJobStorage(new FastJobStorage(JobDatabase.getInstance(context)))
                                                                  .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(PushProcessMessageJob.KEY, MarkerJob.KEY))
                                                                  .addReservedJobRunner(new FactoryJobPredicate(IndividualSendJob.KEY, PushGroupSendJob.KEY, ReactionSendJob.KEY, TypingSendJob.KEY, GroupCallUpdateSendJob.KEY))
                                                                  .build();
    return new JobManager(context, config);
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  @SuppressLint("DiscouragedApi")
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(context);
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver(@NonNull SignalWebSocket.AuthenticatedWebSocket webSocket) {
    return new IncomingMessageObserver(context, webSocket);
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return new TrimThreadsByDateManager(context);
  }

  @Override
  public @NonNull ViewOnceMessageManager provideViewOnceMessageManager() {
    return new ViewOnceMessageManager(context);
  }

  @Override
  public @NonNull ExpiringStoriesManager provideExpiringStoriesManager() {
    return new ExpiringStoriesManager(context);
  }

  @Override
  public @NonNull ExpiringMessageManager provideExpiringMessageManager() {
    return new ExpiringMessageManager(context);
  }

  @Override
  public @NonNull DeletedCallEventManager provideDeletedCallEventManager() {
    return new DeletedCallEventManager(context);
  }

  @Override
  public @NonNull ScheduledMessageManager provideScheduledMessageManager() {
    return new ScheduledMessageManager(context);
  }

  @Override
  public @NonNull Network provideLibsignalNetwork(@NonNull SignalServiceConfiguration config) {
    Network network = new Network(BuildConfig.LIBSIGNAL_NET_ENV, StandardUserAgentInterceptor.USER_AGENT);
    LibSignalNetworkExtensions.applyConfiguration(network, config);

    return network;
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return new TypingStatusRepository();
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return new TypingStatusSender();
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return new DatabaseObserver();
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public @NonNull Payments providePayments(@NonNull PaymentsApi paymentsApi) {
    MobileCoinConfig network;

    if      (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("mainnet")) network = MobileCoinConfig.getMainNet(paymentsApi);
    else if (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("testnet")) network = MobileCoinConfig.getTestNet(paymentsApi);
    else throw new AssertionError("Unknown network " + BuildConfig.MOBILE_COIN_ENVIRONMENT);

    return new Payments(network);
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return new ShakeToReport(context);
  }

  @Override
  public @NonNull SignalCallManager provideSignalCallManager() {
    return new SignalCallManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager() {
    return new PendingRetryReceiptManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache() {
    return new PendingRetryReceiptCache();
  }

  @Override
  public @NonNull SignalWebSocket.AuthenticatedWebSocket provideAuthWebSocket(@NonNull Supplier<SignalServiceConfiguration> signalServiceConfigurationSupplier, @NonNull Supplier<Network> libSignalNetworkSupplier) {
    SleepTimer                   sleepTimer    = !SignalStore.account().isFcmEnabled() || SignalStore.internal().isWebsocketModeForced() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    SignalWebSocketHealthMonitor healthMonitor = new SignalWebSocketHealthMonitor(sleepTimer);

    WebSocketFactory authFactory = () -> {
      DynamicCredentialsProvider credentialsProvider = new DynamicCredentialsProvider();

      if (credentialsProvider.isInvalid()) {
        throw new WebSocketUnavailableException("Invalid auth credentials");
      }

      if (RemoteConfig.libSignalWebSocketEnabled()) {
        Network network = libSignalNetworkSupplier.get();
        return new LibSignalChatConnection("libsignal-auth",
                                           network,
                                           credentialsProvider,
                                           Stories.isFeatureEnabled(),
                                           healthMonitor);
      } else {
        return new OkHttpWebSocketConnection("auth",
                                             signalServiceConfigurationSupplier.get(),
                                             Optional.of(credentialsProvider),
                                             BuildConfig.SIGNAL_AGENT,
                                             healthMonitor,
                                             Stories.isFeatureEnabled());
      }
    };

    SignalWebSocket.AuthenticatedWebSocket webSocket = new SignalWebSocket.AuthenticatedWebSocket(authFactory, sleepTimer, TimeUnit.SECONDS.toMillis(10));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(SignalWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);

    return webSocket;
  }

  @Override
  public @NonNull SignalWebSocket.UnauthenticatedWebSocket provideUnauthWebSocket(@NonNull Supplier<SignalServiceConfiguration> signalServiceConfigurationSupplier, @NonNull Supplier<Network> libSignalNetworkSupplier) {
    SleepTimer                   sleepTimer    = !SignalStore.account().isFcmEnabled() || SignalStore.internal().isWebsocketModeForced() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    SignalWebSocketHealthMonitor healthMonitor = new SignalWebSocketHealthMonitor(sleepTimer);

    WebSocketFactory unauthFactory = () -> {
      if (RemoteConfig.libSignalWebSocketEnabled()) {
        Network network = libSignalNetworkSupplier.get();
        return new LibSignalChatConnection("libsignal-unauth",
                                           network,
                                           null,
                                           Stories.isFeatureEnabled(),
                                           healthMonitor);
      } else {
        return new OkHttpWebSocketConnection("unauth",
                                             signalServiceConfigurationSupplier.get(),
                                             Optional.empty(),
                                             BuildConfig.SIGNAL_AGENT,
                                             healthMonitor,
                                             Stories.isFeatureEnabled());
      }
    };

    SignalWebSocket.UnauthenticatedWebSocket webSocket = new SignalWebSocket.UnauthenticatedWebSocket(unauthFactory, sleepTimer, TimeUnit.SECONDS.toMillis(10));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(SignalWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);
    return webSocket;
  }

  @Override
  public @NonNull SignalServiceDataStoreImpl provideProtocolStore() {
    ACI localAci = SignalStore.account().getAci();
    PNI localPni = SignalStore.account().getPni();

    if (localAci == null) {
      throw new IllegalStateException("No ACI set!");
    }

    if (localPni == null) {
      throw new IllegalStateException("No PNI set!");
    }

    boolean needsPreKeyJob = false;

    if (!SignalStore.account().hasAciIdentityKey()) {
      SignalStore.account().generateAciIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (!SignalStore.account().hasPniIdentityKey()) {
      SignalStore.account().generatePniIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (needsPreKeyJob) {
      PreKeysSyncJob.enqueueIfNeeded();
    }

    SignalBaseIdentityKeyStore baseIdentityStore = new SignalBaseIdentityKeyStore(context);

    SignalServiceAccountDataStoreImpl aciStore = new SignalServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localAci),
                                                                                       new SignalKyberPreKeyStore(localAci),
                                                                                       new SignalIdentityKeyStore(baseIdentityStore, () -> SignalStore.account().getAciIdentityKey()),
                                                                                       new TextSecureSessionStore(localAci),
                                                                                       new SignalSenderKeyStore(context));

    SignalServiceAccountDataStoreImpl pniStore = new SignalServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localPni),
                                                                                       new SignalKyberPreKeyStore(localPni),
                                                                                       new SignalIdentityKeyStore(baseIdentityStore, () -> SignalStore.account().getPniIdentityKey()),
                                                                                       new TextSecureSessionStore(localPni),
                                                                                       new SignalSenderKeyStore(context));
    return new SignalServiceDataStoreImpl(context, aciStore, pniStore);
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return new GiphyMp4Cache(ByteUnit.MEGABYTES.toBytes(16));
  }

  @Override
  public @NonNull SimpleExoPlayerPool provideExoPlayerPool() {
    return new SimpleExoPlayerPool(context);
  }

  @Override
  public @NonNull AudioManagerCompat provideAndroidCallAudioManager() {
    return AudioManagerCompat.create(context);
  }

  @Override
  public @NonNull DonationsService provideDonationsService(@NonNull PushServiceSocket pushServiceSocket) {
    return new DonationsService(pushServiceSocket);
  }

  @Override
  public @NonNull ProfileService provideProfileService(@NonNull ClientZkProfileOperations clientZkProfileOperations,
                                                       @NonNull SignalServiceMessageReceiver receiver,
                                                       @NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket,
                                                       @NonNull SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket)
  {
    return new ProfileService(clientZkProfileOperations, receiver, authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull DeadlockDetector provideDeadlockDetector() {
    HandlerThread handlerThread = new HandlerThread("signal-DeadlockDetector", ThreadUtil.PRIORITY_BACKGROUND_THREAD);
    handlerThread.start();
    return new DeadlockDetector(new Handler(handlerThread.getLooper()), TimeUnit.SECONDS.toMillis(5));
  }

  @Override
  public @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return provideClientZkOperations(signalServiceConfiguration).getReceiptOperations();
  }

  @Override
  public @NonNull BillingApi provideBillingApi() {
    return BillingFactory.create(GooglePlayBillingDependencies.INSTANCE, RemoteConfig.messageBackups() && Environment.Backups.supportsGooglePlayBilling());
  }

  @Override
  public @NonNull ArchiveApi provideArchiveApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new ArchiveApi(authWebSocket, unauthWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull KeysApi provideKeysApi(@NonNull PushServiceSocket pushServiceSocket) {
    return new KeysApi(pushServiceSocket);
  }

  @Override
  public @NonNull AttachmentApi provideAttachmentApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new AttachmentApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull LinkDeviceApi provideLinkDeviceApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new LinkDeviceApi(authWebSocket);
  }

  @Override
  public @NonNull RegistrationApi provideRegistrationApi(@NonNull PushServiceSocket pushServiceSocket) {
    return new RegistrationApi(pushServiceSocket);
  }

  @Override
  public @NonNull StorageServiceApi provideStorageServiceApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new StorageServiceApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull AccountApi provideAccountApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new AccountApi(authWebSocket);
  }

  @Override
  public @NonNull UsernameApi provideUsernameApi(@NonNull SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new UsernameApi(unauthWebSocket);
  }

  @Override
  public @NonNull CallingApi provideCallingApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new CallingApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull PaymentsApi providePaymentsApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new PaymentsApi(authWebSocket);
  }

  @Override
  public @NonNull CdsApi provideCdsApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new CdsApi(authWebSocket);
  }

  @Override
  public @NonNull RateLimitChallengeApi provideRateLimitChallengeApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new RateLimitChallengeApi(authWebSocket);
  }

  @Override
  public @NonNull MessageApi provideMessageApi(@NonNull SignalWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull SignalWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new MessageApi(authWebSocket, unauthWebSocket);
  }

  @VisibleForTesting
  static class DynamicCredentialsProvider implements CredentialsProvider {

    @Override
    public ACI getAci() {
      return SignalStore.account().getAci();
    }

    @Override
    public PNI getPni() {
      return SignalStore.account().getPni();
    }

    @Override
    public String getE164() {
      return SignalStore.account().getE164();
    }

    @Override
    public String getPassword() {
      return SignalStore.account().getServicePassword();
    }

    @Override
    public int getDeviceId() {
      return SignalStore.account().getDeviceId();
    }
  }
}
