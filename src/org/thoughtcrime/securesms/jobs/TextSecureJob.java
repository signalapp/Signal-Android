package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import org.thoughtcrime.securesms.dependencies.AxolotlStorageModule.SignedPreKeyStoreFactory;
import org.thoughtcrime.securesms.dependencies.GraphComponent;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;

import javax.inject.Inject;

public abstract class TextSecureJob extends MasterSecretJob implements InjectableType {
  @Inject transient TextSecureMessageReceiver      messageReceiver;
  @Inject transient TextSecureAccountManager       accountManager;
  @Inject transient SignedPreKeyStoreFactory       signedPreKeyStoreFactory;
  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  public TextSecureJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override public void inject(GraphComponent component) {
    component.inject(this);
  }
}
