package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MultiDeviceMessageRequestResponseJob extends BaseJob {

  public static final String KEY = "MultiDeviceMessageRequestResponseJob";

  private static final String TAG = Log.tag(MultiDeviceMessageRequestResponseJob.class);

  private static final String KEY_THREAD_RECIPIENT = "thread_recipient";
  private static final String KEY_TYPE             = "type";

  private final RecipientId threadRecipient;
  private final Type        type;

  public static @NonNull MultiDeviceMessageRequestResponseJob forAccept(@NonNull RecipientId threadRecipient) {
    return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.ACCEPT);
  }

  public static @NonNull MultiDeviceMessageRequestResponseJob forDelete(@NonNull RecipientId threadRecipient) {
    return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.DELETE);
  }

  public static @NonNull MultiDeviceMessageRequestResponseJob forBlock(@NonNull RecipientId threadRecipient) {
    return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK);
  }

  public static @NonNull MultiDeviceMessageRequestResponseJob forBlockAndDelete(@NonNull RecipientId threadRecipient) {
    return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK_AND_DELETE);
  }

  public static @NonNull MultiDeviceMessageRequestResponseJob forBlockAndReportSpam(@NonNull RecipientId threadRecipient) {
    return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK);
  }

  private MultiDeviceMessageRequestResponseJob(@NonNull RecipientId threadRecipient, @NonNull Type type) {
    this(new Parameters.Builder().setQueue("MultiDeviceMessageRequestResponseJob")
                                 .addConstraint(NetworkConstraint.KEY)
                                 .setMaxAttempts(Parameters.UNLIMITED)
                                 .setLifespan(TimeUnit.DAYS.toMillis(1))
                                 .build(),
         threadRecipient,
         type);
  }

  private MultiDeviceMessageRequestResponseJob(@NonNull Parameters parameters,
                                               @NonNull RecipientId threadRecipient,
                                               @NonNull Type type)
  {
    super(parameters);
    this.threadRecipient = threadRecipient;
    this.type            = type;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_THREAD_RECIPIENT, threadRecipient.serialize())
                                    .putInt(KEY_TYPE, type.serialize())
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(threadRecipient);

    if (!recipient.isGroup() && !recipient.hasServiceId()) {
      Log.i(TAG, "Queued for non-group recipient without ServiceId");
      return;
    }

    MessageRequestResponseMessage response;

    if (recipient.isGroup()) {
      response = MessageRequestResponseMessage.forGroup(recipient.getGroupId().get().getDecodedId(), localToRemoteType(type));
    } else if (recipient.isMaybeRegistered()) {
      response = MessageRequestResponseMessage.forIndividual(RecipientUtil.getOrFetchServiceId(context, recipient), localToRemoteType(type));
    } else {
      response = null;
    }

    if (response != null) {
      messageSender.sendSyncMessage(SignalServiceSyncMessage.forMessageRequestResponse(response),
                                    UnidentifiedAccessUtil.getAccessForSync(context));
    } else {
      Log.w(TAG, recipient.getId() + " not registered!");
    }
  }

  private static MessageRequestResponseMessage.Type localToRemoteType(@NonNull Type type) {
    switch (type) {
      case ACCEPT:
        return MessageRequestResponseMessage.Type.ACCEPT;
      case DELETE:
        return MessageRequestResponseMessage.Type.DELETE;
      case BLOCK:
        return MessageRequestResponseMessage.Type.BLOCK;
      case BLOCK_AND_DELETE:
        return MessageRequestResponseMessage.Type.BLOCK_AND_DELETE;
      default:
        return MessageRequestResponseMessage.Type.UNKNOWN;
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  private enum Type {
    UNKNOWN(0), ACCEPT(1), DELETE(2), BLOCK(3), BLOCK_AND_DELETE(4);

    private final int value;

    Type(int value) {
      this.value = value;
    }

    int serialize() {
      return value;
    }

    static @NonNull Type deserialize(int value) {
      for (Type type : Type.values()) {
        if (type.value == value) {
          return type;
        }
      }
      throw new AssertionError("Unknown type: " + value);
    }
  }

  public static final class Factory implements Job.Factory<MultiDeviceMessageRequestResponseJob> {
    @Override
    public @NonNull MultiDeviceMessageRequestResponseJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      RecipientId threadRecipient = RecipientId.from(data.getString(KEY_THREAD_RECIPIENT));
      Type        type            = Type.deserialize(data.getInt(KEY_TYPE));

      return new MultiDeviceMessageRequestResponseJob(parameters, threadRecipient, type);
    }
  }
}
