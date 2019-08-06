package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.gcm.MessageRetriever;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.push.SecurityEventListener;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.RealtimeSleepTimer;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

  private static final String TAG = Log.tag(ApplicationDependencyProvider.class);

  private final Context                    context;
  private final SignalServiceNetworkAccess networkAccess;

  public ApplicationDependencyProvider(@NonNull Context context, @NonNull SignalServiceNetworkAccess networkAccess) {
    this.context       = context.getApplicationContext();
    this.networkAccess = networkAccess;
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager() {
    return new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                           new DynamicCredentialsProvider(context),
                                           BuildConfig.USER_AGENT);
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender() {
      return new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            new SignalProtocolStoreImpl(context),
                                            BuildConfig.USER_AGENT,
                                            TextSecurePreferences.isMultiDevice(context),
                                            Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                            Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                            Optional.of(new SecurityEventListener(context)));
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver() {
    SleepTimer sleepTimer = TextSecurePreferences.isFcmDisabled(context) ? new RealtimeSleepTimer(context)
                                                                         : new UptimeSleepTimer();
    return new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            BuildConfig.USER_AGENT,
                                            new PipeConnectivityListener(),
                                            sleepTimer);
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
  }

  @Override
  public @NonNull IncomingMessageProcessor provideIncomingMessageProcessor() {
    return new IncomingMessageProcessor(context);
  }

  @Override
  public @NonNull MessageRetriever provideMessageRetriever() {
    return new MessageRetriever();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }
  }
}
