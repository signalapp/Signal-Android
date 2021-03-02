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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDexApplication;

import com.google.firebase.iid.FirebaseInstanceId;

import org.conscrypt.Conscrypt;
import org.session.libsession.messaging.MessagingConfiguration;
import org.session.libsession.messaging.avatars.AvatarHelper;
import org.session.libsession.snode.SnodeConfiguration;
import org.session.libsession.utilities.SSKEnvironment;
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;

import org.session.libsession.utilities.dynamiclanguage.LocaleParser;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.thoughtcrime.securesms.loki.api.SessionProtocolImpl;
import org.thoughtcrime.securesms.sskenvironment.ProfileManager;
import org.thoughtcrime.securesms.sskenvironment.ReadReceiptManager;
import org.thoughtcrime.securesms.sskenvironment.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.session.libsession.utilities.preferences.ProfileKeyUtil;
import org.session.libsession.messaging.threads.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobmanager.DependencyInjector;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer;
import org.thoughtcrime.securesms.jobs.FastJobStorage;
import org.thoughtcrime.securesms.jobs.JobManagerFactories;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.logging.AndroidLogger;
import org.session.libsignal.utilities.logging.Log;
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
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol;
import org.thoughtcrime.securesms.loki.utilities.Broadcaster;
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities;
import org.thoughtcrime.securesms.notifications.DefaultMessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.OptimizedMessageNotifier;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener;
import org.thoughtcrime.securesms.util.dynamiclanguage.LocaleParseHelper;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.session.libsignal.service.api.messages.SignalServiceEnvelope;
import org.session.libsignal.service.api.util.StreamDetails;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.loki.api.Poller;
import org.session.libsignal.service.loki.api.PushNotificationAPI;
import org.session.libsignal.service.loki.api.SnodeAPI;
import org.session.libsignal.service.loki.api.SwarmAPI;
import org.session.libsignal.service.loki.api.fileserver.FileServerAPI;
import org.session.libsignal.service.loki.api.opengroups.PublicChatAPI;
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol;
import org.session.libsignal.service.loki.utilities.mentions.MentionsManager;

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
public class ApplicationContext extends MultiDexApplication implements DependencyInjector, DefaultLifecycleObserver {

  public static final String PREFERENCES_NAME = "SecureSMS-Preferences";

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager  expiringMessageManager;
  private TypingStatusRepository  typingStatusRepository;
  private TypingStatusSender      typingStatusSender;
  private JobManager              jobManager;
  private ReadReceiptManager      readReceiptManager;
  private ProfileManager          profileManager;
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
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    MessagingConfiguration.Companion.configure(this,
                                                DatabaseFactory.getStorage(this),
                                                DatabaseFactory.getAttachmentProvider(this),
                                                new SessionProtocolImpl(this));
    SnodeConfiguration.Companion.configure(apiDB, broadcaster);
    if (userPublicKey != null) {
      SwarmAPI.Companion.configureIfNeeded(apiDB);
      SnodeAPI.Companion.configureIfNeeded(userPublicKey, apiDB, broadcaster);
      MentionsManager.Companion.configureIfNeeded(userPublicKey, threadDB, userDB);
    }
    PushNotificationAPI.Companion.configureIfNeeded(BuildConfig.DEBUG);
    setUpStorageAPIIfNeeded();
    resubmitProfilePictureIfNeeded();
    publicChatManager = new PublicChatManager(this);
    updateOpenGroupProfilePicturesIfNeeded();
    if (userPublicKey != null) {
      registerForFCMIfNeeded(false);
    }
    // Set application UI mode (day/night theme) to the user selected one.
    UiModeUtilities.setupUiModeToUserSelected(this);
    // ========
    initializeJobManager();
    initializeExpiringMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeReadReceiptManager();
    initializeProfileManager();
    initializePeriodicTasks();
    initializeWebRtc();
    initializeBlobProvider();
    SSKEnvironment.Companion.configure(getTypingStatusRepository(), getReadReceiptManager(), getProfileManager(), messageNotifier, getExpiringMessageManager());
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    KeyCachingService.onAppForegrounded(this);
    // Loki
    if (poller != null) { poller.setCaughtUp(false); }
    startPollingIfNeeded();
    publicChatManager.markAllAsNotCaughtUp();
    publicChatManager.startPollersIfNeeded();
    MultiDeviceProtocol.syncConfigurationIfNeeded(this);
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    messageNotifier.setVisibleThread(-1);
    // Loki
    if (poller != null) { poller.stopIfNeeded(); }
    if (closedGroupPoller != null) { closedGroupPoller.stopIfNeeded(); }
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

  public void initializeLocaleParser() {
    LocaleParser.Companion.configure(new LocaleParseHelper());
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

  public ReadReceiptManager getReadReceiptManager() { return readReceiptManager; }

  public ProfileManager getProfileManager() { return profileManager; }

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
    GroupDatabase groupDB = DatabaseFactory.getGroupDatabase(this);
    publicChatAPI = new PublicChatAPI(userPublicKey, userPrivateKey, apiDB, userDB, groupDB);
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
    Log.initialize(new AndroidLogger(), persistentLogger);
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

  private void initializeDependencyInjection() {
    communicationModule = new SignalCommunicationModule(this);
    this.objectGraph = ObjectGraph.create(communicationModule);
  }


  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeTypingStatusRepository() {
    this.typingStatusRepository = new TypingStatusRepository();
  }

  private void initializeReadReceiptManager() {
    this.readReceiptManager = new ReadReceiptManager();
  }

  private void initializeProfileManager() {
    this.profileManager = new ProfileManager();
  }

  private void initializeTypingStatusSender() {
    this.typingStatusSender = new TypingStatusSender(this);
  }

  private void initializePeriodicTasks() {
    LocalBackupListener.schedule(this);
    BackgroundPollWorker.schedulePeriodic(this); // Loki

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

  private void initializeBlobProvider() {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    initializeLocaleParser();
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

  private static class ProviderInitializationException extends RuntimeException { }

  // region Loki
  public boolean setUpStorageAPIIfNeeded() {
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (userPublicKey == null || !IdentityKeyUtil.hasIdentityKey(this)) { return false; }
    byte[] userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(this).getPrivateKey().serialize();
    LokiAPIDatabaseProtocol apiDB = DatabaseFactory.getLokiAPIDatabase(this);
    FileServerAPI.Companion.configure(userPublicKey, userPrivateKey, apiDB);
    org.session.libsession.messaging.fileserver.FileServerAPI.Companion.configure(userPublicKey, userPrivateKey, apiDB);
    return true;
  }

  public void registerForFCMIfNeeded(Boolean force) {
    Context context = this;
    FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
      if (!task.isSuccessful()) {
        Log.w("Loki", "FirebaseInstanceId.getInstance().getInstanceId() failed." + task.getException());
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
    poller = new Poller(userPublicKey, apiDB, envelopes -> {
      for (SignalServiceProtos.Envelope envelope : envelopes) {
        new PushContentReceiveJob(context).processEnvelope(new SignalServiceEnvelope(envelope), false);
      }
      return Unit.INSTANCE;
    });
    ClosedGroupPoller.Companion.configureIfNeeded(this);
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
    if (publicChatManager != null) { publicChatManager.stopPollers(); }
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
      Set<String> servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers();
      for (String server : servers) {
        if (profileKey != null) {
          publicChatAPI.setProfilePicture(server, profileKey, url);
        }
      }
    });
  }

  public void clearAllData(boolean isMigratingToV2KeyPair) {
    String token = TextSecurePreferences.getFCMToken(this);
    if (token != null && !token.isEmpty()) {
      LokiPushNotificationManager.unregister(token, this);
    }
    String displayName = TextSecurePreferences.getProfileName(this);
    boolean isUsingFCM = TextSecurePreferences.isUsingFCM(this);
    TextSecurePreferences.clearAll(this);
    if (isMigratingToV2KeyPair) {
      TextSecurePreferences.setIsMigratingKeyPair(this, true);
      TextSecurePreferences.setIsUsingFCM(this, isUsingFCM);
      TextSecurePreferences.setProfileName(this, displayName);
    }
    getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit();
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

  // endregion
}
