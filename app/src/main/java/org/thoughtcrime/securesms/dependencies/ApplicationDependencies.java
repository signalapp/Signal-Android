package org.thoughtcrime.securesms.dependencies;

import android.annotation.SuppressLint;
import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.concurrent.DeadlockDetector;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.thoughtcrime.securesms.KbsEnclave;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.storage.SignalServiceDataStoreImpl;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache;
import org.thoughtcrime.securesms.groups.GroupsV2Authorization;
import org.thoughtcrime.securesms.groups.GroupsV2AuthorizationMemoryValueCache;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.messages.BackgroundMessageRetriever;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.payments.Payments;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.push.SignalServiceTrustStore;
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
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.EarlyMessageCache;
import org.thoughtcrime.securesms.util.FrameRateTracker;
import org.thoughtcrime.securesms.util.IasKeyStore;
import org.thoughtcrime.securesms.video.exo.GiphyMp4Cache;
import org.thoughtcrime.securesms.video.exo.SimpleExoPlayerPool;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.services.DonationsService;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #init(Application, Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
@SuppressLint("StaticFieldLeak")
public class ApplicationDependencies {

  private static final Object LOCK                    = new Object();
  private static final Object FRAME_RATE_TRACKER_LOCK = new Object();
  private static final Object JOB_MANAGER_LOCK        = new Object();
  private static final Object SIGNAL_HTTP_CLIENT_LOCK = new Object();

  private static Application           application;
  private static Provider              provider;
  private static AppForegroundObserver appForegroundObserver;

  private static volatile SignalServiceAccountManager  accountManager;
  private static volatile SignalServiceMessageSender   messageSender;
  private static volatile SignalServiceMessageReceiver messageReceiver;
  private static volatile IncomingMessageObserver      incomingMessageObserver;
  private static volatile BackgroundMessageRetriever   backgroundMessageRetriever;
  private static volatile LiveRecipientCache           recipientCache;
  private static volatile JobManager                   jobManager;
  private static volatile FrameRateTracker             frameRateTracker;
  private static volatile MegaphoneRepository          megaphoneRepository;
  private static volatile GroupsV2Authorization        groupsV2Authorization;
  private static volatile GroupsV2StateProcessor       groupsV2StateProcessor;
  private static volatile GroupsV2Operations           groupsV2Operations;
  private static volatile EarlyMessageCache            earlyMessageCache;
  private static volatile TypingStatusRepository       typingStatusRepository;
  private static volatile TypingStatusSender           typingStatusSender;
  private static volatile DatabaseObserver             databaseObserver;
  private static volatile TrimThreadsByDateManager     trimThreadsByDateManager;
  private static volatile ViewOnceMessageManager       viewOnceMessageManager;
  private static volatile ExpiringStoriesManager       expiringStoriesManager;
  private static volatile ExpiringMessageManager       expiringMessageManager;
  private static volatile DeletedCallEventManager      deletedCallEventManager;
  private static volatile Payments                     payments;
  private static volatile SignalCallManager            signalCallManager;
  private static volatile ShakeToReport                shakeToReport;
  private static volatile OkHttpClient                 okHttpClient;
  private static volatile OkHttpClient                 signalOkHttpClient;
  private static volatile PendingRetryReceiptManager   pendingRetryReceiptManager;
  private static volatile PendingRetryReceiptCache     pendingRetryReceiptCache;
  private static volatile SignalWebSocket              signalWebSocket;
  private static volatile MessageNotifier              messageNotifier;
  private static volatile SignalServiceDataStoreImpl   protocolStore;
  private static volatile GiphyMp4Cache                giphyMp4Cache;
  private static volatile SimpleExoPlayerPool          exoPlayerPool;
  private static volatile AudioManagerCompat           audioManagerCompat;
  private static volatile DonationsService             donationsService;
  private static volatile ProfileService               profileService;
  private static volatile DeadlockDetector             deadlockDetector;
  private static volatile ClientZkReceiptOperations    clientZkReceiptOperations;
  private static volatile ScheduledMessageManager      scheduledMessagesManager;

  @MainThread
  public static void init(@NonNull Application application, @NonNull Provider provider) {
    synchronized (LOCK) {
      if (ApplicationDependencies.application != null || ApplicationDependencies.provider != null) {
        throw new IllegalStateException("Already initialized!");
      }

      ApplicationDependencies.application           = application;
      ApplicationDependencies.provider              = provider;
      ApplicationDependencies.appForegroundObserver = provider.provideAppForegroundObserver();

      ApplicationDependencies.appForegroundObserver.begin();
    }
  }

  @VisibleForTesting
  public static boolean isInitialized() {
    return ApplicationDependencies.application != null;
  }

  public static @NonNull Application getApplication() {
    return application;
  }

  public static @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    SignalServiceAccountManager local = accountManager;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (accountManager == null) {
        accountManager = provider.provideSignalServiceAccountManager(getSignalServiceNetworkAccess().getConfiguration(), getGroupsV2Operations());
      }
      return accountManager;
    }
  }

  public static @NonNull GroupsV2Authorization getGroupsV2Authorization() {
    if (groupsV2Authorization == null) {
      synchronized (LOCK) {
        if (groupsV2Authorization == null) {
          GroupsV2Authorization.ValueCache authCache = new GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AciAuthorizationCache());

          groupsV2Authorization = new GroupsV2Authorization(getSignalServiceAccountManager().getGroupsV2Api(), authCache);
        }
      }
    }

    return groupsV2Authorization;
  }

  public static @NonNull GroupsV2Operations getGroupsV2Operations() {
    if (groupsV2Operations == null) {
      synchronized (LOCK) {
        if (groupsV2Operations == null) {
          groupsV2Operations = provider.provideGroupsV2Operations(getSignalServiceNetworkAccess().getConfiguration());
        }
      }
    }

    return groupsV2Operations;
  }

  public static @NonNull KeyBackupService getKeyBackupService(@NonNull KbsEnclave enclave) {
    return provider.provideKeyBackupService(getSignalServiceAccountManager(), IasKeyStore.getIasKeyStore(application), enclave);
  }

  public static @NonNull GroupsV2StateProcessor getGroupsV2StateProcessor() {
    if (groupsV2StateProcessor == null) {
      synchronized (LOCK) {
        if (groupsV2StateProcessor == null) {
          groupsV2StateProcessor = new GroupsV2StateProcessor(application);
        }
      }
    }

    return groupsV2StateProcessor;
  }

  public static @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
    SignalServiceMessageSender local = messageSender;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (messageSender == null) {
        messageSender = provider.provideSignalServiceMessageSender(getSignalWebSocket(), getProtocolStore(), getSignalServiceNetworkAccess().getConfiguration());
      }
      return messageSender;
    }
  }

  public static @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    synchronized (LOCK) {
      if (messageReceiver == null) {
        messageReceiver = provider.provideSignalServiceMessageReceiver(getSignalServiceNetworkAccess().getConfiguration());
      }
      return messageReceiver;
    }
  }

  public static void resetSignalServiceMessageReceiver() {
    synchronized (LOCK) {
      messageReceiver = null;
    }
  }

  public static void closeConnections() {
    synchronized (LOCK) {
      if (incomingMessageObserver != null) {
        incomingMessageObserver.terminateAsync();
      }

      if (messageSender != null) {
        messageSender.cancelInFlightRequests();
      }

      incomingMessageObserver = null;
      messageReceiver         = null;
      accountManager          = null;
      messageSender           = null;
    }
  }

  public static void resetAllNetworkConnections() {
    synchronized (LOCK) {
      closeConnections();
      if (signalWebSocket != null) {
        signalWebSocket.forceNewWebSockets();
      }
    }
  }

  public static @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    return provider.provideSignalServiceNetworkAccess();
  }

  public static @NonNull BackgroundMessageRetriever getBackgroundMessageRetriever() {
    if (backgroundMessageRetriever == null) {
      synchronized (LOCK) {
        if (backgroundMessageRetriever == null) {
          backgroundMessageRetriever = provider.provideBackgroundMessageRetriever();
        }
      }
    }

    return backgroundMessageRetriever;
  }

  public static @NonNull LiveRecipientCache getRecipientCache() {
    if (recipientCache == null) {
      synchronized (LOCK) {
        if (recipientCache == null) {
          recipientCache = provider.provideRecipientCache();
        }
      }
    }

    return recipientCache;
  }

  public static @NonNull JobManager getJobManager() {
    if (jobManager == null) {
      synchronized (JOB_MANAGER_LOCK) {
        if (jobManager == null) {
          jobManager = provider.provideJobManager();
        }
      }
    }

    return jobManager;
  }

  public static @NonNull FrameRateTracker getFrameRateTracker() {
    if (frameRateTracker == null) {
      synchronized (FRAME_RATE_TRACKER_LOCK) {
        if (frameRateTracker == null) {
          frameRateTracker = provider.provideFrameRateTracker();
        }
      }
    }

    return frameRateTracker;
  }

  public static @NonNull MegaphoneRepository getMegaphoneRepository() {
    if (megaphoneRepository == null) {
      synchronized (LOCK) {
        if (megaphoneRepository == null) {
          megaphoneRepository = provider.provideMegaphoneRepository();
        }
      }
    }

    return megaphoneRepository;
  }

  public static @NonNull EarlyMessageCache getEarlyMessageCache() {
    if (earlyMessageCache == null) {
      synchronized (LOCK) {
        if (earlyMessageCache == null) {
          earlyMessageCache = provider.provideEarlyMessageCache();
        }
      }
    }

    return earlyMessageCache;
  }

  public static @NonNull MessageNotifier getMessageNotifier() {
    if (messageNotifier == null) {
      synchronized (LOCK) {
        if (messageNotifier == null) {
          messageNotifier = provider.provideMessageNotifier();
        }
      }
    }
    return messageNotifier;
  }

  public static @NonNull IncomingMessageObserver getIncomingMessageObserver() {
    IncomingMessageObserver local = incomingMessageObserver;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (incomingMessageObserver == null) {
        incomingMessageObserver = provider.provideIncomingMessageObserver();
      }
      return incomingMessageObserver;
    }
  }

  public static @NonNull TrimThreadsByDateManager getTrimThreadsByDateManager() {
    if (trimThreadsByDateManager == null) {
      synchronized (LOCK) {
        if (trimThreadsByDateManager == null) {
          trimThreadsByDateManager = provider.provideTrimThreadsByDateManager();
        }
      }
    }

    return trimThreadsByDateManager;
  }

  public static @NonNull ViewOnceMessageManager getViewOnceMessageManager() {
    if (viewOnceMessageManager == null) {
      synchronized (LOCK) {
        if (viewOnceMessageManager == null) {
          viewOnceMessageManager = provider.provideViewOnceMessageManager();
        }
      }
    }

    return viewOnceMessageManager;
  }

  public static @NonNull ExpiringStoriesManager getExpireStoriesManager() {
    if (expiringStoriesManager == null) {
      synchronized (LOCK) {
        if (expiringStoriesManager == null) {
          expiringStoriesManager = provider.provideExpiringStoriesManager();
        }
      }
    }

    return expiringStoriesManager;
  }

  public static @NonNull PendingRetryReceiptManager getPendingRetryReceiptManager() {
    if (pendingRetryReceiptManager == null) {
      synchronized (LOCK) {
        if (pendingRetryReceiptManager == null) {
          pendingRetryReceiptManager = provider.providePendingRetryReceiptManager();
        }
      }
    }

    return pendingRetryReceiptManager;
  }

  public static @NonNull ExpiringMessageManager getExpiringMessageManager() {
    if (expiringMessageManager == null) {
      synchronized (LOCK) {
        if (expiringMessageManager == null) {
          expiringMessageManager = provider.provideExpiringMessageManager();
        }
      }
    }

    return expiringMessageManager;
  }

  public static @NonNull DeletedCallEventManager getDeletedCallEventManager() {
    if (deletedCallEventManager == null) {
      synchronized (LOCK) {
        if (deletedCallEventManager == null) {
          deletedCallEventManager = provider.provideDeletedCallEventManager();
        }
      }
    }

    return deletedCallEventManager;
  }

  public static @NonNull ScheduledMessageManager getScheduledMessageManager() {
    if (scheduledMessagesManager == null) {
      synchronized (LOCK) {
        if (scheduledMessagesManager == null) {
          scheduledMessagesManager = provider.provideScheduledMessageManager();
        }
      }
    }

    return scheduledMessagesManager;
  }

  public static TypingStatusRepository getTypingStatusRepository() {
    if (typingStatusRepository == null) {
      synchronized (LOCK) {
        if (typingStatusRepository == null) {
          typingStatusRepository = provider.provideTypingStatusRepository();
        }
      }
    }

    return typingStatusRepository;
  }

  public static TypingStatusSender getTypingStatusSender() {
    if (typingStatusSender == null) {
      synchronized (LOCK) {
        if (typingStatusSender == null) {
          typingStatusSender = provider.provideTypingStatusSender();
        }
      }
    }

    return typingStatusSender;
  }

  public static @NonNull DatabaseObserver getDatabaseObserver() {
    if (databaseObserver == null) {
      synchronized (LOCK) {
        if (databaseObserver == null) {
          databaseObserver = provider.provideDatabaseObserver();
        }
      }
    }

    return databaseObserver;
  }

  public static @NonNull Payments getPayments() {
    if (payments == null) {
      synchronized (LOCK) {
        if (payments == null) {
          payments = provider.providePayments(getSignalServiceAccountManager());
        }
      }
    }

    return payments;
  }

  public static @NonNull ShakeToReport getShakeToReport() {
    if (shakeToReport == null) {
      synchronized (LOCK) {
        if (shakeToReport == null) {
          shakeToReport = provider.provideShakeToReport();
        }
      }
    }

    return shakeToReport;
  }

  public static @NonNull SignalCallManager getSignalCallManager() {
    if (signalCallManager == null) {
      synchronized (LOCK) {
        if (signalCallManager == null) {
          signalCallManager = provider.provideSignalCallManager();
        }
      }
    }

    return signalCallManager;
  }

  public static @NonNull OkHttpClient getOkHttpClient() {
    if (okHttpClient == null) {
      synchronized (LOCK) {
        if (okHttpClient == null) {
          okHttpClient = new OkHttpClient.Builder()
              .addInterceptor(new StandardUserAgentInterceptor())
              .dns(SignalServiceNetworkAccess.DNS)
              .build();
        }
      }
    }

    return okHttpClient;
  }

  public static @NonNull OkHttpClient getSignalOkHttpClient() {
    if (signalOkHttpClient == null) {
      synchronized (SIGNAL_HTTP_CLIENT_LOCK) {
        if (signalOkHttpClient == null) {
          try {
            OkHttpClient   baseClient    = ApplicationDependencies.getOkHttpClient();
            SSLContext     sslContext    = SSLContext.getInstance("TLS");
            TrustStore     trustStore    = new SignalServiceTrustStore(ApplicationDependencies.getApplication());
            TrustManager[] trustManagers = BlacklistingTrustManager.createFor(trustStore);

            sslContext.init(null, trustManagers, null);

            signalOkHttpClient = baseClient.newBuilder()
                                           .sslSocketFactory(new Tls12SocketFactory(sslContext.getSocketFactory()), (X509TrustManager) trustManagers[0])
                                           .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                                           .build();
          } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new AssertionError(e);
          }
        }
      }
    }

    return signalOkHttpClient;
  }

  public static @NonNull AppForegroundObserver getAppForegroundObserver() {
    return appForegroundObserver;
  }

  public static @NonNull PendingRetryReceiptCache getPendingRetryReceiptCache() {
    if (pendingRetryReceiptCache == null) {
      synchronized (LOCK) {
        if (pendingRetryReceiptCache == null) {
          pendingRetryReceiptCache = provider.providePendingRetryReceiptCache();
        }
      }
    }

    return pendingRetryReceiptCache;
  }

  public static @NonNull SignalWebSocket getSignalWebSocket() {
    if (signalWebSocket == null) {
      synchronized (LOCK) {
        if (signalWebSocket == null) {
          signalWebSocket = provider.provideSignalWebSocket(() -> getSignalServiceNetworkAccess().getConfiguration());
        }
      }
    }
    return signalWebSocket;
  }

  public static @NonNull SignalServiceDataStoreImpl getProtocolStore() {
    if (protocolStore == null) {
      synchronized (LOCK) {
        if (protocolStore == null) {
          protocolStore = provider.provideProtocolStore();
        }
      }
    }

    return protocolStore;
  }

  public static @NonNull GiphyMp4Cache getGiphyMp4Cache() {
    if (giphyMp4Cache == null) {
      synchronized (LOCK) {
        if (giphyMp4Cache == null) {
          giphyMp4Cache = provider.provideGiphyMp4Cache();
        }
      }
    }
    return giphyMp4Cache;
  }

  public static @NonNull SimpleExoPlayerPool getExoPlayerPool() {
    if (exoPlayerPool == null) {
      synchronized (LOCK) {
        if (exoPlayerPool == null) {
          exoPlayerPool = provider.provideExoPlayerPool();
        }
      }
    }
    return exoPlayerPool;
  }

  public static @NonNull AudioManagerCompat getAndroidCallAudioManager() {
    if (audioManagerCompat == null) {
      synchronized (LOCK) {
        if (audioManagerCompat == null) {
          audioManagerCompat = provider.provideAndroidCallAudioManager();
        }
      }
    }
    return audioManagerCompat;
  }

  public static @NonNull DonationsService getDonationsService() {
    if (donationsService == null) {
      synchronized (LOCK) {
        if (donationsService == null) {
          donationsService = provider.provideDonationsService(getSignalServiceNetworkAccess().getConfiguration(), getGroupsV2Operations());
        }
      }
    }
    return donationsService;
  }

  public static @NonNull ProfileService getProfileService() {
    if (profileService == null) {
      synchronized (LOCK) {
        if (profileService == null) {
          profileService = provider.provideProfileService(ApplicationDependencies.getGroupsV2Operations().getProfileOperations(),
                                                          ApplicationDependencies.getSignalServiceMessageReceiver(),
                                                          ApplicationDependencies.getSignalWebSocket());
        }
      }
    }
    return profileService;
  }

  public static @NonNull ClientZkReceiptOperations getClientZkReceiptOperations() {
    if (clientZkReceiptOperations == null) {
      synchronized (LOCK) {
        if (clientZkReceiptOperations == null) {
          clientZkReceiptOperations = provider.provideClientZkReceiptOperations(getSignalServiceNetworkAccess().getConfiguration());
        }
      }
    }
    return clientZkReceiptOperations;
  }

  public static @NonNull DeadlockDetector getDeadlockDetector() {
    if (deadlockDetector == null) {
      synchronized (LOCK) {
        if (deadlockDetector == null) {
          deadlockDetector = provider.provideDeadlockDetector();
        }
      }
    }
    return deadlockDetector;
  }

  public interface Provider {
    @NonNull GroupsV2Operations provideGroupsV2Operations(@NonNull SignalServiceConfiguration signalServiceConfiguration);
    @NonNull SignalServiceAccountManager provideSignalServiceAccountManager(@NonNull SignalServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations);
    @NonNull SignalServiceMessageSender provideSignalServiceMessageSender(@NonNull SignalWebSocket signalWebSocket, @NonNull SignalServiceDataStore protocolStore, @NonNull SignalServiceConfiguration signalServiceConfiguration);
    @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver(@NonNull SignalServiceConfiguration signalServiceConfiguration);
    @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();
    @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever();
    @NonNull LiveRecipientCache provideRecipientCache();
    @NonNull JobManager provideJobManager();
    @NonNull FrameRateTracker provideFrameRateTracker();
    @NonNull MegaphoneRepository provideMegaphoneRepository();
    @NonNull EarlyMessageCache provideEarlyMessageCache();
    @NonNull MessageNotifier provideMessageNotifier();
    @NonNull IncomingMessageObserver provideIncomingMessageObserver();
    @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager();
    @NonNull ViewOnceMessageManager provideViewOnceMessageManager();
    @NonNull ExpiringStoriesManager provideExpiringStoriesManager();
    @NonNull ExpiringMessageManager provideExpiringMessageManager();
    @NonNull DeletedCallEventManager provideDeletedCallEventManager();
    @NonNull TypingStatusRepository provideTypingStatusRepository();
    @NonNull TypingStatusSender provideTypingStatusSender();
    @NonNull DatabaseObserver provideDatabaseObserver();
    @NonNull Payments providePayments(@NonNull SignalServiceAccountManager signalServiceAccountManager);
    @NonNull ShakeToReport provideShakeToReport();
    @NonNull AppForegroundObserver provideAppForegroundObserver();
    @NonNull SignalCallManager provideSignalCallManager();
    @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager();
    @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache();
    @NonNull SignalWebSocket provideSignalWebSocket(@NonNull Supplier<SignalServiceConfiguration> signalServiceConfigurationSupplier);
    @NonNull SignalServiceDataStoreImpl provideProtocolStore();
    @NonNull GiphyMp4Cache provideGiphyMp4Cache();
    @NonNull SimpleExoPlayerPool provideExoPlayerPool();
    @NonNull AudioManagerCompat provideAndroidCallAudioManager();
    @NonNull DonationsService provideDonationsService(@NonNull SignalServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations);
    @NonNull ProfileService provideProfileService(@NonNull ClientZkProfileOperations profileOperations, @NonNull SignalServiceMessageReceiver signalServiceMessageReceiver, @NonNull SignalWebSocket signalWebSocket);
    @NonNull DeadlockDetector provideDeadlockDetector();
    @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations(@NonNull SignalServiceConfiguration signalServiceConfiguration);
    @NonNull KeyBackupService provideKeyBackupService(@NonNull SignalServiceAccountManager signalServiceAccountManager, @NonNull KeyStore keyStore, @NonNull KbsEnclave enclave);
    @NonNull ScheduledMessageManager provideScheduledMessageManager();
  }
}
