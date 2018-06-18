package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class RequestGroupInfoJob extends ContextJob implements InjectableType {

  private static final String TAG = RequestGroupInfoJob.class.getSimpleName();

  private static final long serialVersionUID = 0L;

  @Inject transient SignalServiceMessageSender messageSender;

  private final String source;
  private final byte[] groupId;

  public RequestGroupInfoJob(@NonNull Context context, @NonNull String source, @NonNull byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withRetryCount(50)
                                .create());

    this.source  = source;
    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    SignalServiceGroup       group   = SignalServiceGroup.newBuilder(Type.REQUEST_INFO)
                                                         .withId(groupId)
                                                         .build();

    SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                                                               .asGroupMessage(group)
                                                               .withTimestamp(System.currentTimeMillis())
                                                               .build();

    messageSender.sendMessage(new SignalServiceAddress(source), message);
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {

  }
}
