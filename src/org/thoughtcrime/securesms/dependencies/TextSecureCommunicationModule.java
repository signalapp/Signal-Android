package org.thoughtcrime.securesms.dependencies;

import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.push.SecurityEventListener;
import org.thoughtcrime.securesms.push.TextSecurePushTrustStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides TextSecureAccountManager provideTextSecureAccountManager() {
    return new TextSecureAccountManager(BuildConfig.PUSH_URL,
                                        new TextSecurePushTrustStore(context),
                                        TextSecurePreferences.getLocalNumber(context),
                                        TextSecurePreferences.getPushServerPassword(context));
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public TextSecureMessageSender create() {
        return new TextSecureMessageSender(BuildConfig.PUSH_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           new TextSecureAxolotlStore(context),
                                           Optional.of((TextSecureMessageSender.EventListener)
                                                           new SecurityEventListener(context)));
      }
    };
  }

  @Provides TextSecureMessageReceiver provideTextSecureMessageReceiver() {
    return new TextSecureMessageReceiver(BuildConfig.PUSH_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context));
  }

  public interface TextSecureMessageSenderFactory {
    TextSecureMessageSender create();
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

}
