package org.thoughtcrime.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.gcm.MessageRetriever;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.keyvalue.KeyValueStore;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.LiveRecipientCache;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.EarlyMessageCache;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.FrameRateTracker;
import org.thoughtcrime.securesms.util.IasKeyStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #init(Application, Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

  private static Application application;
  private static Provider    provider;

  private static SignalServiceAccountManager       accountManager;
  private static SignalServiceMessageSender        messageSender;
  private static SignalServiceMessageReceiver      messageReceiver;
  private static IncomingMessageProcessor          incomingMessageProcessor;
  private static MessageRetriever                  messageRetriever;
  private static LiveRecipientCache                recipientCache;
  private static JobManager                        jobManager;
  private static FrameRateTracker                  frameRateTracker;
  private static KeyValueStore                     keyValueStore;
  private static MegaphoneRepository               megaphoneRepository;
  private static GroupsV2Operations                groupsV2Operations;
  private static EarlyMessageCache                 earlyMessageCache;

  public static synchronized void init(@NonNull Application application, @NonNull Provider provider) {
    if (ApplicationDependencies.application != null || ApplicationDependencies.provider != null) {
      throw new IllegalStateException("Already initialized!");
    }

    ApplicationDependencies.application = application;
    ApplicationDependencies.provider    = provider;
  }

  public static @NonNull Application getApplication() {
    assertInitialization();
    return application;
  }

  public static synchronized @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    assertInitialization();

    if (accountManager == null) {
      accountManager = provider.provideSignalServiceAccountManager();
    }

    return accountManager;
  }

  public static synchronized @NonNull GroupsV2Operations getGroupsV2Operations() {
    assertInitialization();

    if (groupsV2Operations == null) {
      groupsV2Operations = provider.provideGroupsV2Operations();
    }

    return groupsV2Operations;
  }

  public static synchronized @NonNull KeyBackupService getKeyBackupService() {
    return getSignalServiceAccountManager().getKeyBackupService(IasKeyStore.getIasKeyStore(application),
                                                                BuildConfig.KBS_ENCLAVE_NAME,
                                                                BuildConfig.KBS_MRENCLAVE,
                                                                10);
  }

  public static synchronized @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
    assertInitialization();

    if (messageSender == null) {
      messageSender = provider.provideSignalServiceMessageSender();
    } else {
      messageSender.update(
              IncomingMessageObserver.getPipe(),
              IncomingMessageObserver.getUnidentifiedPipe(),
              TextSecurePreferences.isMultiDevice(application),
              FeatureFlags.attachmentsV3());
    }

    return messageSender;
  }

  public static synchronized @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    assertInitialization();

    if (messageReceiver == null) {
      messageReceiver = provider.provideSignalServiceMessageReceiver();
    }

    return messageReceiver;
  }

  public static synchronized void resetSignalServiceMessageReceiver() {
    assertInitialization();
    messageReceiver = null;
  }

  public static synchronized @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    assertInitialization();
    return provider.provideSignalServiceNetworkAccess();
  }

  public static synchronized @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
    assertInitialization();

    if (incomingMessageProcessor == null) {
      incomingMessageProcessor = provider.provideIncomingMessageProcessor();
    }

    return incomingMessageProcessor;
  }

  public static synchronized @NonNull MessageRetriever getMessageRetriever() {
    assertInitialization();

    if (messageRetriever == null) {
      messageRetriever = provider.provideMessageRetriever();
    }

    return messageRetriever;
  }

  public static synchronized @NonNull LiveRecipientCache getRecipientCache() {
    assertInitialization();

    if (recipientCache == null) {
      recipientCache = provider.provideRecipientCache();
    }

    return recipientCache;
  }

  public static synchronized @NonNull JobManager getJobManager() {
    assertInitialization();

    if (jobManager == null) {
      jobManager = provider.provideJobManager();
    }

    return jobManager;
  }

  public static synchronized @NonNull FrameRateTracker getFrameRateTracker() {
    assertInitialization();

    if (frameRateTracker == null) {
      frameRateTracker = provider.provideFrameRateTracker();
    }

    return frameRateTracker;
  }

  public static synchronized @NonNull KeyValueStore getKeyValueStore() {
    assertInitialization();

    if (keyValueStore == null) {
      keyValueStore = provider.provideKeyValueStore();
    }

    return keyValueStore;
  }

  public static synchronized @NonNull MegaphoneRepository getMegaphoneRepository() {
    assertInitialization();

    if (megaphoneRepository == null) {
      megaphoneRepository = provider.provideMegaphoneRepository();
    }

    return megaphoneRepository;
  }

  public static synchronized @NonNull EarlyMessageCache getEarlyMessageCache() {
    assertInitialization();

    if (earlyMessageCache == null) {
      earlyMessageCache = provider.provideEarlyMessageCache();
    }

    return earlyMessageCache;
  }

  private static void assertInitialization() {
    if (application == null || provider == null) {
      throw new UninitializedException();
    }
  }

  public interface Provider {
    @NonNull GroupsV2Operations provideGroupsV2Operations();
    @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();
    @NonNull SignalServiceMessageSender provideSignalServiceMessageSender();
    @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver();
    @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();
    @NonNull IncomingMessageProcessor provideIncomingMessageProcessor();
    @NonNull MessageRetriever provideMessageRetriever();
    @NonNull LiveRecipientCache provideRecipientCache();
    @NonNull JobManager provideJobManager();
    @NonNull FrameRateTracker provideFrameRateTracker();
    @NonNull KeyValueStore provideKeyValueStore();
    @NonNull MegaphoneRepository provideMegaphoneRepository();
    @NonNull EarlyMessageCache provideEarlyMessageCache();
  }

  private static class UninitializedException extends IllegalStateException {
    private UninitializedException() {
      super("You must call init() first!");
    }
  }
}
