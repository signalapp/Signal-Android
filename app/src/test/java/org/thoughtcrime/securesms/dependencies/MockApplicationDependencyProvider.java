package org.thoughtcrime.securesms.dependencies;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.DeadlockDetector;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.thoughtcrime.securesms.KbsEnclave;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.crypto.storage.SignalServiceDataStoreImpl;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.PendingRetryReceiptCache;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.messages.BackgroundMessageRetriever;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.payments.Payments;
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
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.EarlyMessageCache;
import org.thoughtcrime.securesms.util.FrameRateTracker;
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
import org.whispersystems.signalservice.api.services.DonationsService;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.security.KeyStore;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

@SuppressWarnings("ConstantConditions")
public class MockApplicationDependencyProvider implements ApplicationDependencies.Provider {
  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return null;
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager(@NonNull SignalServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations) {
    return null;
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender(@NonNull SignalWebSocket signalWebSocket, @NonNull SignalServiceDataStore protocolStore, @NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return null;
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return null;
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return null;
  }

  @Override
  public @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever() {
    return null;
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return null;
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    return mock(JobManager.class);
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return null;
  }

  @Override
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return null;
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return null;
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return null;
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver() {
    return null;
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return null;
  }

  @Override
  public @NonNull ViewOnceMessageManager provideViewOnceMessageManager() {
    return null;
  }

  @Override
  public @NonNull ExpiringStoriesManager provideExpiringStoriesManager() {
    return null;
  }

  @Override
  public @NonNull ExpiringMessageManager provideExpiringMessageManager() {
    return null;
  }

  @Override
  public @NonNull DeletedCallEventManager provideDeletedCallEventManager() {
    return null;
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return null;
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return null;
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return mock(DatabaseObserver.class);
  }

  @Override
  public @NonNull Payments providePayments(@NonNull SignalServiceAccountManager signalServiceAccountManager) {
    return null;
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return null;
  }

  @Override
  public @NonNull AppForegroundObserver provideAppForegroundObserver() {
    return mock(AppForegroundObserver.class);
  }

  @Override
  public @NonNull SignalCallManager provideSignalCallManager() {
    return null;
  }

  @Override
  public @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager() {
    return null;
  }

  @Override
  public @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache() {
    return null;
  }

  @Override
  public @NonNull SignalWebSocket provideSignalWebSocket(@NonNull Supplier<SignalServiceConfiguration> signalServiceConfigurationSupplier) {
    return null;
  }

  @Override
  public @NonNull SignalServiceDataStoreImpl provideProtocolStore() {
    return null;
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return null;
  }

  @Override
  public @NonNull SimpleExoPlayerPool provideExoPlayerPool() {
    return null;
  }

  @Override
  public @NonNull AudioManagerCompat provideAndroidCallAudioManager() {
    return null;
  }

  @Override
  public @NonNull DonationsService provideDonationsService(@NonNull SignalServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations) {
    return null;
  }

  @Override
  public @NonNull ProfileService provideProfileService(@NonNull ClientZkProfileOperations profileOperations, @NonNull SignalServiceMessageReceiver signalServiceMessageReceiver, @NonNull SignalWebSocket signalWebSocket) {
    return null;
  }

  @Override
  public @NonNull DeadlockDetector provideDeadlockDetector() {
    return null;
  }

  @Override
  public @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations(@NonNull SignalServiceConfiguration signalServiceConfiguration) {
    return null;
  }

  @Override
  public @NonNull KeyBackupService provideKeyBackupService(@NonNull SignalServiceAccountManager signalServiceAccountManager, @NonNull KeyStore keyStore, @NonNull KbsEnclave enclave) {
    return null;
  }

  @Override
  public @NonNull ScheduledMessageManager provideScheduledMessageManager() {
    return null;
  }
}
