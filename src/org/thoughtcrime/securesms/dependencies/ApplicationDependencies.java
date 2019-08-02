package org.thoughtcrime.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #init(Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

  private static ApplicationDependencies instance;

  private final Provider provider;

  private ApplicationDependencies(@NonNull Provider provider) {
    this.provider = provider;
  }

  public static synchronized void init(@NonNull Provider provider) {
    instance = new ApplicationDependencies(provider);
  }

  public static synchronized @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    assertInitialization();
    return instance.provider.getSignalServiceAccountManager();
  }

  public static synchronized @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
    assertInitialization();
    return instance.provider.getSignalServiceMessageSender();
  }

  public static synchronized @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    assertInitialization();
    return instance.provider.getSignalServiceMessageReceiver();
  }

  public static synchronized @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    assertInitialization();
    return instance.provider.getSignalServiceNetworkAccess();
  }

  private static void assertInitialization() {
    if (instance == null) {
      throw new UninitializedException();
    }
  }

  public interface Provider {
    @NonNull SignalServiceAccountManager getSignalServiceAccountManager();
    @NonNull SignalServiceMessageSender getSignalServiceMessageSender();
    @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver();
    @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess();
  }

  private static class UninitializedException extends IllegalStateException {
    private UninitializedException() {
      super("You must call init() first!");
    }
  }
}
