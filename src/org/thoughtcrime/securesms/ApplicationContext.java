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
import android.content.Context;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDexApplication;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.security.ProviderInstaller;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.conscrypt.Conscrypt;
import org.jetbrains.annotations.NotNull;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
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
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
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
import org.thoughtcrime.securesms.loki.BackgroundPollWorker;
import org.thoughtcrime.securesms.loki.LokiAPIDatabase;
import org.thoughtcrime.securesms.loki.LokiGroupChatPoller;
import org.thoughtcrime.securesms.loki.LokiRSSFeedPoller;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
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
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.loki.api.*;
import org.whispersystems.signalservice.loki.utilities.Analytics;

import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.ObjectGraph;
import io.fabric.sdk.android.Fabric;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import network.loki.messenger.BuildConfig;

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

  private ExpiringMessageManager  expiringMessageManager;
  private TypingStatusRepository  typingStatusRepository;
  private TypingStatusSender      typingStatusSender;
  private JobManager              jobManager;
  private IncomingMessageObserver incomingMessageObserver;
  private ObjectGraph             objectGraph;
  private PersistentLogger        persistentLogger;

  // Loki
  private LokiLongPoller lokiLongPoller = null;
  private LokiGroupChatPoller lokiPublicChatPoller = null;
  private LokiRSSFeedPoller lokiNewsFeedPoller = null;
  private LokiRSSFeedPoller lokiMessengerUpdatesFeedPoller = null;
  public SignalCommunicationModule communicationModule;
  public MixpanelAPI mixpanel;

  private volatile boolean isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    LokiGroupChatAPI.Companion.setDebugMode(BuildConfig.DEBUG); // Loki - Set debug mode if needed
    Log.i(TAG, "onCreate()");
    initializeSecurityProvider();
    initializeLogging();
    initializeCrashHandling();
    initializeDependencyInjection();
    initializeJobManager();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeGcmCheck();
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
    setUpStorageAPI();
    // Loki - Set up beta analytics
    if (!BuildConfig.DEBUG) {
      Fabric.with(this, new Crashlytics());
    }
    mixpanel = MixpanelAPI.getInstance(this, "59040b6707e5a1725f3fb6730fefca92");
    Analytics.Companion.getShared().trackImplementation = (Function1<String, Unit>) event -> {
      HashMap<String, Object> properties = new HashMap();
      String configuration = BuildConfig.DEBUG ? "debug" : "production";
      properties.put("configuration", configuration);
      mixpanel.trackMap(event, properties);
      return Unit.INSTANCE;
    };
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
    // Loki - Start long polling if needed
    startLongPollingIfNeeded();
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    MessageNotifier.setVisibleThread(-1);
    // Loki - Stop long polling if needed
    if (lokiLongPoller != null) { lokiLongPoller.stopIfNeeded(); }
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

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new FcmRefreshJob());
      }
    }
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
  public void setUpStorageAPI() {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
    LokiAPIDatabaseProtocol database = DatabaseFactory.getLokiAPIDatabase(this);
    LokiStorageAPI.Companion.configure(userHexEncodedPublicKey, userPrivateKey, database);
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
    lokiLongPoller = new LokiLongPoller(userHexEncodedPublicKey, lokiAPIDatabase, protos -> {
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

  private LokiGroupChat lokiPublicChat() {
    return new LokiGroupChat(LokiGroupChatAPI.getPublicChatServerID(), LokiGroupChatAPI.getPublicChatServer(), "Loki Public Chat", true);
  }

  private LokiRSSFeed lokiNewsFeed() {
    return new LokiRSSFeed("loki.network.feed", "https://loki.network/feed/", "Loki News", true);
  }

  private LokiRSSFeed lokiMessengerUpdatesFeed() {
    return new LokiRSSFeed("loki.network.messenger-updates.feed", "https://loki.network/category/messenger-updates/feed", "Loki Messenger Updates", false);
  }

  public void createGroupChatsIfNeeded() {
    LokiGroupChat publicChat = lokiPublicChat();
    boolean isChatSetUp = TextSecurePreferences.isChatSetUp(this, publicChat.getId());
    if (!isChatSetUp || !publicChat.isDeletable()) {
      GroupManager.GroupActionResult result = GroupManager.createGroup(publicChat.getId(), this, new HashSet<>(), null, publicChat.getDisplayName(), false);
      TextSecurePreferences.markChatSetUp(this, publicChat.getId());
    }
  }

  public void createRSSFeedsIfNeeded() {
    ArrayList<LokiRSSFeed> feeds = new ArrayList<>();
    feeds.add(lokiNewsFeed());
    feeds.add(lokiMessengerUpdatesFeed());
    for (LokiRSSFeed feed : feeds) {
      boolean isFeedSetUp = TextSecurePreferences.isChatSetUp(this, feed.getId());
      if (!isFeedSetUp || !feed.isDeletable()) {
        GroupManager.createGroup(feed.getId(), this, new HashSet<>(), null, feed.getDisplayName(), false);
        TextSecurePreferences.markChatSetUp(this, feed.getId());
      }
    }
  }

  private void createGroupChatPollersIfNeeded() {
    // Only create the group chat pollers if their threads aren't deleted
    LokiGroupChat publicChat = lokiPublicChat();
    long threadID = GroupManager.getThreadId(publicChat.getId(), this);
    if (threadID >= 0 && lokiPublicChatPoller == null) {
      lokiPublicChatPoller = new LokiGroupChatPoller(this, publicChat);
      // Set up deletion listeners if needed
      setUpThreadDeletionListeners(threadID, () -> {
        if (lokiPublicChatPoller != null) lokiPublicChatPoller.stop();
        lokiPublicChatPoller = null;
      });
    }
  }

  private void createRSSFeedPollersIfNeeded() {
    // Only create the RSS feed pollers if their threads aren't deleted
    LokiRSSFeed lokiNewsFeed = lokiNewsFeed();
    long lokiNewsFeedThreadID = GroupManager.getThreadId(lokiNewsFeed.getId(), this);
    if (lokiNewsFeedThreadID >= 0 && lokiNewsFeedPoller == null) {
      lokiNewsFeedPoller = new LokiRSSFeedPoller(this, lokiNewsFeed);
      // Set up deletion listeners if needed
      setUpThreadDeletionListeners(lokiNewsFeedThreadID, () -> {
        if (lokiNewsFeedPoller != null) lokiNewsFeedPoller.stop();
        lokiNewsFeedPoller = null;
      });
    }
    // The user can't delete the Loki Messenger Updates RSS feed
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

  public void startGroupChatPollersIfNeeded() {
    createGroupChatPollersIfNeeded();
    if (lokiPublicChatPoller != null) lokiPublicChatPoller.startIfNeeded();
  }

  public void startRSSFeedPollersIfNeeded() {
    createRSSFeedPollersIfNeeded();
    if (lokiNewsFeedPoller != null) lokiNewsFeedPoller.startIfNeeded();
    if (lokiMessengerUpdatesFeedPoller != null) lokiMessengerUpdatesFeedPoller.startIfNeeded();
  }
  // endregion
}
