/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.google.firebase.iid.FirebaseInstanceId;

import org.conscrypt.Conscrypt;
import org.jetbrains.annotations.NotNull;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.AxolotlStorageModule;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobmanager.DependencyInjector;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.FastJobStorage;
import org.thoughtcrime.securesms.jobs.JobManagerFactories;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import org.thoughtcrime.securesms.logging.AndroidLogger;
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.logging.PersistentLogger;
import org.thoughtcrime.securesms.logging.UncaughtExceptionLogger;
import org.thoughtcrime.securesms.loki.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.api.BackgroundPollWorker;
import org.thoughtcrime.securesms.loki.api.ClosedGroupPoller;
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager;
import org.thoughtcrime.securesms.loki.api.PublicChatManager;
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase;
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.database.LokiUserDatabase;
import org.thoughtcrime.securesms.loki.database.SharedSenderKeysDatabase;
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol;
import org.thoughtcrime.securesms.loki.protocol.SessionRequestMessageSendJob;
import org.thoughtcrime.securesms.loki.protocol.SessionResetImplementation;
import org.thoughtcrime.securesms.loki.utilities.Broadcaster;
import org.thoughtcrime.securesms.notifications.DefaultMessageNotifier;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.OptimizedMessageNotifier;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.service.RotateSenderCertificateListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.loki.api.Poller;
import org.whispersystems.signalservice.loki.api.PushNotificationAcknowledgement;
import org.whispersystems.signalservice.loki.api.SnodeAPI;
import org.whispersystems.signalservice.loki.api.SwarmAPI;
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI;
import org.whispersystems.signalservice.loki.api.opengroups.PublicChatAPI;
import org.whispersystems.signalservice.loki.api.shelved.p2p.LokiP2PAPI;
import org.whispersystems.signalservice.loki.api.shelved.p2p.LokiP2PAPIDelegate;
import org.whispersystems.signalservice.loki.database.LokiAPIDatabaseProtocol;
import org.whispersystems.signalservice.loki.protocol.closedgroups.SharedSenderKeysImplementation;
import org.whispersystems.signalservice.loki.protocol.closedgroups.SharedSenderKeysImplementationDelegate;
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager;
import org.whispersystems.signalservice.loki.protocol.meta.SessionMetaProtocol;
import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities;
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLink;
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol;
import org.whispersystems.signalservice.loki.protocol.sessionmanagement.SessionManagementProtocol;
import org.whispersystems.signalservice.loki.protocol.sessionmanagement.SessionManagementProtocolDelegate;
import org.whispersystems.signalservice.loki.protocol.shelved.syncmessages.SyncMessagesProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import dagger.ObjectGraph;
import kotlin.Unit;
import network.loki.messenger.BuildConfig;

import static nl.komponents.kovenant.android.KovenantAndroid.startKovenant;
import static nl.komponents.kovenant.android.KovenantAndroid.stopKovenant;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector, DefaultLifecycleObserver, LokiP2PAPIDelegate,
      SessionManagementProtocolDelegate, SharedSenderKeysImplementationDelegate {

  private static final String TAG = ApplicationContext.class.getSimpleName();
  private final static int OK_HTTP_CACHE_SIZE = 10 * 1024 * 1024; // 10 MB

  private ExpiringMessageManager  expiringMessageManager;
  private TypingStatusRepository  typingStatusRepository;
  private TypingStatusSender      typingStatusSender;
  private JobManager              jobManager;
  private IncomingMessageObserver incomingMessageObserver;
  private ObjectGraph             objectGraph;
  private PersistentLogger        persistentLogger;

  // Loki
  public MessageNotifier messageNotifier = null;
  public Poller poller = null;
  public ClosedGroupPoller closedGroupPoller = null;
  public PublicChatManager publicChatManager = null;
  private PublicChatAPI publicChatAPI = null;
  public Broadcaster broadcaster = null;
  public SignalCommunicationModule communicationModule;

  private volatile boolean isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate()");
    startKovenant();
    initializeSecurityProvider();
    initializeLogging();
    initializeCrashHandling();
    initializeDependencyInjection();
    NotificationChannels.create(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    // Loki
    // ========
    messageNotifier = new OptimizedMessageNotifier(new DefaultMessageNotifier());
    broadcaster = new Broadcaster(this);
    LokiAPIDatabase apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    LokiThreadDatabase threadDB = DatabaseFactory.getLokiThreadDatabase(this);
    LokiUserDatabase userDB = DatabaseFactory.getLokiUserDatabase(this);
    SharedSenderKeysDatabase sskDatabase = DatabaseFactory.getSSKDatabase(this);
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    SessionResetImplementation sessionResetImpl = new SessionResetImplementation(this);
    SharedSenderKeysImplementation.Companion.configureIfNeeded(sskDatabase, this);
    if (userPublicKey != null) {
      SwarmAPI.Companion.configureIfNeeded(apiDB);
      SnodeAPI.Companion.configureIfNeeded(userPublicKey, apiDB, broadcaster);
      MentionsManager.Companion.configureIfNeeded(userPublicKey, threadDB, userDB);
      SessionMetaProtocol.Companion.configureIfNeeded(apiDB, userPublicKey);
      SyncMessagesProtocol.Companion.configureIfNeeded(apiDB, userPublicKey);
    }
    MultiDeviceProtocol.Companion.configureIfNeeded(apiDB);
    SessionManagementProtocol.Companion.configureIfNeeded(sessionResetImpl, sskDatabase, this);
    setUpP2PAPIIfNeeded();
    PushNotificationAcknowledgement.Companion.configureIfNeeded(BuildConfig.DEBUG);
    if (setUpStorageAPIIfNeeded()) {
      if (userPublicKey != null) {
        Set<DeviceLink> deviceLinks = DatabaseFactory.getLokiAPIDatabase(this).getDeviceLinks(userPublicKey);
        FileServerAPI.shared.setDeviceLinks(deviceLinks);
      }
    }
    resubmitProfilePictureIfNeeded();
    publicChatManager = new PublicChatManager(this);
    updateOpenGroupProfilePicturesIfNeeded();
    registerForFCMIfNeeded(false);
    // ========
    initializeJobManager();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeWebRtc();
    initializePendingMessages();
    initializeUnidentifiedDeliveryAbilityRefresh();
    initializeBlobProvider();
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
    // Loki
    if (poller != null) { poller.setCaughtUp(false); }
    startPollingIfNeeded();
    publicChatManager.markAllAsNotCaughtUp();
    publicChatManager.startPollersIfNeeded();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    messageNotifier.setVisibleThread(-1);
    // Loki
    if (poller != null) { poller.stopIfNeeded(); }
    if (publicChatManager != null) { publicChatManager.stopPollers(); }
  }

  @Override
  public void onTerminate() {
    stopKovenant(); // Loki
    super.onTerminate();
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public TypingStatusRepository getTypingStatusRepository() {
    return typingStatusRepository;
  }

  public TypingStatusSender getTypingStatusSender() {
    return typingStatusSender;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  // Loki
  public @Nullable PublicChatAPI getPublicChatAPI() {
    if (publicChatAPI != null || !IdentityKeyUtil.hasIdentityKey(this)) { return publicChatAPI; }
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userPublicKey== null) { return publicChatAPI; }
    byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
    LokiAPIDatabase apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    LokiUserDatabase userDB = DatabaseFactory.getLokiUserDatabase(this);
    publicChatAPI = new PublicChatAPI(userPublicKey, userPrivateKey, apiDB, userDB);
    return publicChatAPI;
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    persistentLogger = new PersistentLogger(this);
    org.thoughtcrime.securesms.logging.Log.initialize(new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger(originalHandler));
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, new JobManager.Configuration.Builder()
                                                                       .setDataSerializer(new JsonDataSerializer())
                                                                       .setJobFactories(JobManagerFactories.getJobFactories(this))
                                                                       .setConstraintFactories(JobManagerFactories.getConstraintFactories(this))
                                                                       .setConstraintObservers(JobManagerFactories.getConstraintObservers(this))
                                                                       .setJobStorage(new FastJobStorage(DatabaseFactory.getJobDatabase(this)))
                                                                       .setDependencyInjector(this)
                                                                       .build());
  }

  public void initializeMessageRetrieval() {
    this.incomingMessageObserver = new IncomingMessageObserver(this);
  }

  private void initializeDependencyInjection() {
    communicationModule = new SignalCommunicationModule(this, new SignalServiceNetworkAccess(this));
    this.objectGraph = ObjectGraph.create(communicationModule, new AxolotlStorageModule(this));
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeTypingStatusRepository() {
    this.typingStatusRepository = new TypingStatusRepository();
  }

  private void initializeTypingStatusSender() {
    this.typingStatusSender = new TypingStatusSender(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    BackgroundPollWorker.schedule(this); // Loki

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
        add("E5823"); // Sony z5 compact
        add("Redmi Note 5");
        add("FP2"); // Fairphone FP2
        add("MI 5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      PeerConnectionFactory.initialize(InitializationOptions.builder(this).createInitializationOptions());
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationContext.getInstance(this).getJobManager().add(new MultiDeviceContactUpdateJob(this, true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeUnidentifiedDeliveryAbilityRefresh() {
    if (TextSecurePreferences.isMultiDevice(this) && !TextSecurePreferences.isUnidentifiedDeliveryEnabled(this)) {
      jobManager.add(new RefreshUnidentifiedDeliveryAbilityJob());
    }
  }

  private void initializeBlobProvider() {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

  private static class ProviderInitializationException extends RuntimeException { }

  // region Loki
  public boolean setUpStorageAPIIfNeeded() {
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userPublicKey == null || !IdentityKeyUtil.hasIdentityKey(this)) { return false; }
    boolean isDebugMode = BuildConfig.DEBUG;
    byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
    LokiAPIDatabaseProtocol apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    FileServerAPI.Companion.configure(userPublicKey, userPrivateKey, apiDB);
    return true;
  }

  public void setUpP2PAPIIfNeeded() {
    String hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (hexEncodedPublicKey == null) { return; }
    LokiP2PAPI.Companion.configure(hexEncodedPublicKey, (isOnline, contactPublicKey) -> {
      // TODO: Implement
      return null;
    }, this);
  }

  public void registerForFCMIfNeeded(Boolean force) {
    Context context = this;
    FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
      if (!task.isSuccessful()) {
        Log.w(TAG, "getInstanceId failed", task.getException());
        return;
      }
      String token = task.getResult().getToken();
      String userPublicKey = TextSecurePreferences.getLocalNumber(context);
      if (userPublicKey == null) return;
      if (TextSecurePreferences.isUsingFCM(this)) {
        LokiPushNotificationManager.register(token, userPublicKey, context, force);
      } else {
        LokiPushNotificationManager.unregister(token, context);
      }
    });
  }

  @Override
  public void ping(@NotNull String s) {
    // TODO: Implement
  }

  private void setUpPollingIfNeeded() {
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userPublicKey == null) return;
    if (poller != null) {
      SnodeAPI.shared.setUserPublicKey(userPublicKey);
      poller.setUserPublicKey(userPublicKey);
      return;
    }
    LokiAPIDatabase apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    Context context = this;
    SwarmAPI.Companion.configureIfNeeded(apiDB);
    SnodeAPI.Companion.configureIfNeeded(userPublicKey, apiDB, broadcaster);
    poller = new Poller(userPublicKey, apiDB, protos -> {
      for (SignalServiceProtos.Envelope proto : protos) {
        new PushContentReceiveJob(context).processEnvelope(new SignalServiceEnvelope(proto), false);
      }
      return Unit.INSTANCE;
    });
    SharedSenderKeysDatabase sskDatabase = DatabaseFactory.getSSKDatabase(this);
    ClosedGroupPoller.Companion.configureIfNeeded(this, sskDatabase);
    closedGroupPoller = ClosedGroupPoller.Companion.getShared();
  }

  public void startPollingIfNeeded() {
    setUpPollingIfNeeded();
    if (poller != null) { poller.startIfNeeded(); }
    if (closedGroupPoller != null) { closedGroupPoller.startIfNeeded(); }
  }

  public void stopPolling() {
    if (poller != null) { poller.stopIfNeeded(); }
    if (closedGroupPoller != null) { closedGroupPoller.stopIfNeeded(); }
  }

  private void resubmitProfilePictureIfNeeded() {
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userPublicKey == null) return;
    long now = new Date().getTime();
    long lastProfilePictureUpload = TextSecurePreferences.getLastProfilePictureUpload(this);
    if (now - lastProfilePictureUpload <= 14 * 24 * 60 * 60 * 1000) return;
    AsyncTask.execute(() -> {
      String encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this);
      byte[] profileKey = ProfileKeyUtil.getProfileKeyFromEncodedString(encodedProfileKey);
      try {
        File profilePicture = AvatarHelper.getAvatarFile(this, Address.fromSerialized(userPublicKey));
        StreamDetails stream = new StreamDetails(new FileInputStream(profilePicture), "image/jpeg", profilePicture.length());
        FileServerAPI.shared.uploadProfilePicture(FileServerAPI.shared.getServer(), profileKey, stream, () -> {
          TextSecurePreferences.setLastProfilePictureUpload(this, new Date().getTime());
          TextSecurePreferences.setProfileAvatarId(this, new SecureRandom().nextInt());
          ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey);
          return Unit.INSTANCE;
        });
      } catch (Exception exception) {
        // Do nothing
      }
    });
  }

  public void updateOpenGroupProfilePicturesIfNeeded() {
    AsyncTask.execute(() -> {
      PublicChatAPI publicChatAPI = null;
      try {
        publicChatAPI = getPublicChatAPI();
      } catch (Exception e) {
        // Do nothing
      }
      if (publicChatAPI == null) { return; }
      byte[] profileKey = ProfileKeyUtil.getProfileKey(this);
      String url = TextSecurePreferences.getProfilePictureURL(this);
      String userMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(this);
      if (userMasterDevice != null) {
        Recipient userMasterDeviceAsRecipient = Recipient.from(this, Address.fromSerialized(userMasterDevice), false).resolve();
        profileKey = userMasterDeviceAsRecipient.getProfileKey();
        url = userMasterDeviceAsRecipient.getProfileAvatar();
      }
      Set<String> servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers();
      for (String server : servers) {
        if (profileKey != null) {
          publicChatAPI.setProfilePicture(server, profileKey, url);
        }
      }
    });
  }

  public void clearData() {
    String token = TextSecurePreferences.getFCMToken(this);
    if (token != null && !token.isEmpty()) {
      LokiPushNotificationManager.unregister(token, this);
    }
    boolean wasUnlinked = TextSecurePreferences.getWasUnlinked(this);
    TextSecurePreferences.clearAll(this);
    TextSecurePreferences.setWasUnlinked(this, wasUnlinked);
    MasterSecretUtil.clear(this);
    if (!deleteDatabase("signal.db")) {
      Log.d("Loki", "Failed to delete database.");
    }
    Util.runOnMain(() -> new Handler().postDelayed(ApplicationContext.this::restartApplication, 200));
  }

  public void restartApplication() {
    Intent intent = new Intent(this, HomeActivity.class);
    startActivity(Intent.makeRestartActivityTask(intent.getComponent()));
    Runtime.getRuntime().exit(0);
  }

  public boolean hasSentSessionRequestExpired(@NotNull String publicKey) {
    LokiAPIDatabase apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    Long timestamp = apiDB.getSessionRequestSentTimestamp(publicKey);
    if (timestamp != null) {
      long expiration = timestamp + TTLUtilities.getTTL(TTLUtilities.MessageType.SessionRequest);
      return new Date().getTime() > expiration;
    } else {
      return false;
    }
  }

  @Override
  public void sendSessionRequestIfNeeded(@NotNull String publicKey) {
    // It's never necessary to establish a session with self
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (publicKey.equals(userPublicKey)) { return; }
    // Check that we don't already have a session
    SignalProtocolAddress address = new SignalProtocolAddress(publicKey, SignalServiceAddress.DEFAULT_DEVICE_ID);
    boolean hasSession = new TextSecureSessionStore(this).containsSession(address);
    if (hasSession) { return; }
    // Check that we didn't already send a session request
    LokiAPIDatabase apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    boolean hasSentSessionRequest = (apiDB.getSessionRequestSentTimestamp(publicKey) != null);
    boolean hasSentSessionRequestExpired = hasSentSessionRequestExpired(publicKey);
    if (hasSentSessionRequestExpired) {
      apiDB.setSessionRequestSentTimestamp(publicKey, 0);
    }
    if (hasSentSessionRequest && !hasSentSessionRequestExpired) { return; }
    // Send the session request
    long timestamp = new Date().getTime();
    apiDB.setSessionRequestSentTimestamp(publicKey, timestamp);
    SessionRequestMessageSendJob job = new SessionRequestMessageSendJob(publicKey, timestamp);
    jobManager.add(job);
  }

  @Override
  public void requestSenderKey(@NotNull String groupPublicKey, @NotNull String senderPublicKey) {
    ClosedGroupsProtocol.requestSenderKey(this, groupPublicKey, senderPublicKey);
  }
  // endregion
}
