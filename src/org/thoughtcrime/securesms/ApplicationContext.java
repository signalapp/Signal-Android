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

import android.annotation.SuppressLint;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;
import org.jetbrains.annotations.NotNull;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.AxolotlStorageModule;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.groups.GroupManager;
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
import org.thoughtcrime.securesms.loki.LokiPublicChatManager;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
import org.thoughtcrime.securesms.loki.redesign.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.redesign.messaging.BackgroundPollWorker;
import org.thoughtcrime.securesms.loki.redesign.messaging.BackgroundPublicChatPollWorker;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiAPIDatabase;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiRSSFeedPoller;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiUserDatabase;
import org.thoughtcrime.securesms.loki.redesign.utilities.Broadcaster;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.service.RotateSenderCertificateListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.loki.api.LokiAPIDatabaseProtocol;
import org.whispersystems.signalservice.loki.api.LokiFileServerAPI;
import org.whispersystems.signalservice.loki.api.LokiLongPoller;
import org.whispersystems.signalservice.loki.api.LokiP2PAPI;
import org.whispersystems.signalservice.loki.api.LokiP2PAPIDelegate;
import org.whispersystems.signalservice.loki.api.LokiPublicChat;
import org.whispersystems.signalservice.loki.api.LokiPublicChatAPI;
import org.whispersystems.signalservice.loki.api.LokiRSSFeed;

import java.io.File;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
public class ApplicationContext extends MultiDexApplication implements DependencyInjector, DefaultLifecycleObserver, LokiP2PAPIDelegate {

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
  private LokiLongPoller lokiLongPoller = null;
  private LokiRSSFeedPoller lokiNewsFeedPoller = null;
  private LokiRSSFeedPoller lokiMessengerUpdatesFeedPoller = null;
  private LokiPublicChatManager lokiPublicChatManager = null;
  private LokiPublicChatAPI lokiPublicChatAPI = null;
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
    broadcaster = new Broadcaster(this);
    checkNeedsDatabaseReset();
    startKovenant();
    initializeSecurityProvider();
    initializeLogging();
    initializeCrashHandling();
    initializeDependencyInjection();
    initializeJobManager();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    initializeWebRtc();
    initializePendingMessages();
    initializeUnidentifiedDeliveryAbilityRefresh();
    initializeBlobProvider();
    NotificationChannels.create(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    // Loki - Set up P2P API if needed
    setUpP2PAPI();
    // Loki - Update device mappings
    if (setUpStorageAPIIfNeeded()) {
      String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
      if (userHexEncodedPublicKey != null) {
        if (TextSecurePreferences.getNeedsIsRevokedSlaveDeviceCheck(this)) {
          MultiDeviceUtilities.checkIsRevokedSlaveDevice(this);
        } else {
          // We always update our current device links onto the server in case we failed to do so upon linking
          MultiDeviceUtilities.updateDeviceLinksOnServer(this);
        }
      }
    }
    // Loki - Resubmit profile picture if needed
    resubmitProfilePictureIfNeeded();
    // Loki - Set up public chat manager
    lokiPublicChatManager = new LokiPublicChatManager(this);
    updatePublicChatProfilePictureIfNeeded();
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
    // Loki - Start long polling if needed
    startLongPollingIfNeeded();
    // Loki - Start open group polling if needed
    lokiPublicChatManager.startPollersIfNeeded();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    MessageNotifier.setVisibleThread(-1);
    // Loki - Stop long polling if needed
    if (lokiLongPoller != null) { lokiLongPoller.stopIfNeeded(); }
    if (lokiPublicChatManager != null) { lokiPublicChatManager.stopPollers(); }
  }

  @Override
  public void onTerminate() {
    stopKovenant();
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

  public LokiPublicChatManager getLokiPublicChatManager() {
    return lokiPublicChatManager;
  }

  public @Nullable LokiPublicChatAPI getLokiPublicChatAPI() {
    if (lokiPublicChatAPI == null && IdentityKeyUtil.hasIdentityKey(this)) {
      String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
      if (userHexEncodedPublicKey != null) {
        byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
        LokiAPIDatabase apiDatabase = DatabaseFactory.getLokiAPIDatabase(this);
        LokiUserDatabase userDatabase = DatabaseFactory.getLokiUserDatabase(this);
        lokiPublicChatAPI = new LokiPublicChatAPI(userHexEncodedPublicKey, userPrivateKey, apiDatabase, userDatabase);
      }
    }
    return lokiPublicChatAPI;
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
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    BackgroundPollWorker.schedule(this); // Session
    BackgroundPublicChatPollWorker.schedule(this); // Session

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

  @SuppressLint("StaticFieldLeak")
  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

  private static class ProviderInitializationException extends RuntimeException {
  }

  // region Loki
  public boolean setUpStorageAPIIfNeeded() {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userHexEncodedPublicKey != null && IdentityKeyUtil.hasIdentityKey(this)) {
      boolean isDebugMode = BuildConfig.DEBUG;
      byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
      LokiAPIDatabaseProtocol database = DatabaseFactory.getLokiAPIDatabase(this);
      LokiFileServerAPI.Companion.configure(isDebugMode, userHexEncodedPublicKey, userPrivateKey, database);
      return true;
    }
    return false;
  }

  public void setUpP2PAPI() {
    String hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (hexEncodedPublicKey == null) { return; }
    LokiP2PAPI.Companion.configure(hexEncodedPublicKey, (isOnline, contactPublicKey) -> {
      // TODO: Implement
      return null;
    }, this);
  }

  @Override
  public void ping(@NotNull String s) {
    // TODO: Implement
  }

  private void setUpLongPollingIfNeeded() {
    if (lokiLongPoller != null) return;
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userHexEncodedPublicKey == null) return;
    LokiAPIDatabase lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(this);
    Context context = this;
    lokiLongPoller = new LokiLongPoller(userHexEncodedPublicKey, lokiAPIDatabase, broadcaster, protos -> {
      for (SignalServiceProtos.Envelope proto : protos) {
        new PushContentReceiveJob(context).processEnvelope(new SignalServiceEnvelope(proto));
      }
      return Unit.INSTANCE;
    });
  }

  public void startLongPollingIfNeeded() {
    setUpLongPollingIfNeeded();
    if (lokiLongPoller != null) { lokiLongPoller.startIfNeeded(); }
  }

  private LokiRSSFeed lokiNewsFeed() {
    return new LokiRSSFeed("loki.network.feed", "https://loki.network/feed/", "Loki News", true);
  }

  private LokiRSSFeed lokiMessengerUpdatesFeed() {
    return new LokiRSSFeed("loki.network.messenger-updates.feed", "https://loki.network/category/messenger-updates/feed", "Session Updates", false);
  }

  public void createDefaultPublicChatsIfNeeded() {
    List<LokiPublicChat> defaultPublicChats = LokiPublicChatAPI.Companion.getDefaultChats(BuildConfig.DEBUG);
    for (LokiPublicChat publicChat : defaultPublicChats) {
      long threadID = GroupManager.getPublicChatThreadId(publicChat.getId(), this);
      String migrationKey = publicChat.getId() + "_migrated";
      boolean isChatMigrated = TextSecurePreferences.getBooleanPreference(this, migrationKey, false);
      boolean isChatSetUp = TextSecurePreferences.isChatSetUp(this, publicChat.getId());
      if (!isChatSetUp || !publicChat.isDeletable()) {
        lokiPublicChatManager.addChat(publicChat.getServer(), publicChat.getChannel(), publicChat.getDisplayName());
        TextSecurePreferences.markChatSetUp(this, publicChat.getId());
        TextSecurePreferences.setBooleanPreference(this, migrationKey, true);
      } else if (threadID > -1 && !isChatMigrated) {
        // Migrate the old public chats
        DatabaseFactory.getLokiThreadDatabase(this).setPublicChat(publicChat, threadID);
        TextSecurePreferences.setBooleanPreference(this, migrationKey, true);
      }
    }
  }

  public void createRSSFeedsIfNeeded() {
    ArrayList<LokiRSSFeed> feeds = new ArrayList<>();
//    feeds.add(lokiNewsFeed());
    feeds.add(lokiMessengerUpdatesFeed());
    for (LokiRSSFeed feed : feeds) {
      boolean isFeedSetUp = TextSecurePreferences.isChatSetUp(this, feed.getId());
      if (!isFeedSetUp || !feed.isDeletable()) {
        GroupManager.createRSSFeedGroup(feed.getId(), this, null, feed.getDisplayName());
        TextSecurePreferences.markChatSetUp(this, feed.getId());
      }
    }
  }

  private void createRSSFeedPollersIfNeeded() {
    // Only create the RSS feed pollers if their threads aren't deleted
    LokiRSSFeed lokiNewsFeed = lokiNewsFeed();
    long lokiNewsFeedThreadID = GroupManager.getRSSFeedThreadId(lokiNewsFeed.getId(), this);
    if (lokiNewsFeedThreadID >= 0 && lokiNewsFeedPoller == null) {
      lokiNewsFeedPoller = new LokiRSSFeedPoller(this, lokiNewsFeed);
      // Set up deletion listeners if needed
      setUpThreadDeletionListeners(lokiNewsFeedThreadID, () -> {
        if (lokiNewsFeedPoller != null) lokiNewsFeedPoller.stop();
        lokiNewsFeedPoller = null;
      });
    }
    // The user can't delete the Session Updates RSS feed
    if (lokiMessengerUpdatesFeedPoller == null) {
      lokiMessengerUpdatesFeedPoller = new LokiRSSFeedPoller(this, lokiMessengerUpdatesFeed());
    }
  }

  private void setUpThreadDeletionListeners(long threadID, Runnable onDelete) {
    if (threadID < 0) { return; }
    ContentObserver observer = new ContentObserver(null) {

      @Override
      public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        // Stop the poller if thread is deleted
        try {
          if (!DatabaseFactory.getThreadDatabase(getApplicationContext()).hasThread(threadID)) {
            onDelete.run();
            getContentResolver().unregisterContentObserver(this);
          }
        } catch (Exception e) {
          // TODO: Handle
        }
      }
    };
    this.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadID), true, observer);
  }

  public void startRSSFeedPollersIfNeeded() {
    createRSSFeedPollersIfNeeded();
    if (lokiNewsFeedPoller != null) lokiNewsFeedPoller.startIfNeeded();
    if (lokiMessengerUpdatesFeedPoller != null) lokiMessengerUpdatesFeedPoller.startIfNeeded();
  }

  private void resubmitProfilePictureIfNeeded() {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userHexEncodedPublicKey == null) return;
    long now = new Date().getTime();
    long lastProfilePictureUpload = TextSecurePreferences.getLastProfilePictureUpload(this);
    if (now - lastProfilePictureUpload <= 14 * 24 * 60 * 60 * 1000) return;
    AsyncTask.execute(() -> {
      String encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this);
      byte[] profileKey = ProfileKeyUtil.getProfileKeyFromEncodedString(encodedProfileKey);
      try {
        File profilePicture = AvatarHelper.getAvatarFile(this, Address.fromSerialized(userHexEncodedPublicKey));
        StreamDetails stream = new StreamDetails(new FileInputStream(profilePicture), "image/jpeg", profilePicture.length());
        LokiFileServerAPI.shared.uploadProfilePicture(LokiFileServerAPI.shared.getServer(), profileKey, stream, () -> {
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

  public void updatePublicChatProfilePictureIfNeeded() {
    AsyncTask.execute(() -> {
      LokiPublicChatAPI publicChatAPI = null;
      try {
        publicChatAPI = getLokiPublicChatAPI();
      } catch (Exception e) {
        // Do nothing
      }
      if (publicChatAPI != null) {
        byte[] profileKey = ProfileKeyUtil.getProfileKey(this);
        String url = TextSecurePreferences.getProfileAvatarUrl(this);
        String ourMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(this);
        if (ourMasterDevice != null) {
          Recipient masterDevice = Recipient.from(this, Address.fromSerialized(ourMasterDevice), false).resolve();
          profileKey = masterDevice.getProfileKey();
          url = masterDevice.getProfileAvatar();
        }
        Set<String> servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers();
        for (String server : servers) {
          if (profileKey != null) {
            publicChatAPI.setProfilePicture(server, profileKey, url);
          }
        }
      }
    });
  }

  public void checkNeedsDatabaseReset() {
    if (TextSecurePreferences.resetDatabase(this)) {
      boolean wasUnlinked = TextSecurePreferences.databaseResetFromUnpair(this);
      TextSecurePreferences.clearAll(this);
      TextSecurePreferences.setDatabaseResetFromUnpair(this, wasUnlinked); // Loki - Re-set the preference so we can use it in the starting screen to determine whether device was unlinked or not
      MasterSecretUtil.clear(this);
      if (this.deleteDatabase("signal.db")) {
        Log.d("Loki", "Deleted database");
      }
    }
  }

  public void clearData() {
    TextSecurePreferences.setResetDatabase(this, true);
    new Handler().postDelayed(this::restartApplication, 200);
  }

  public void restartApplication() {
    Intent intent = new Intent(this, HomeActivity.class);
    ComponentName componentName = intent.getComponent();
    Intent mainIntent = Intent.makeRestartActivityTask(componentName);
    this.startActivity(mainIntent);
    Runtime.getRuntime().exit(0);
  }
  // endregion
}
