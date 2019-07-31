package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class MultiDeviceViewOnceOpenJob extends BaseJob {

  public static final String KEY = "MultiDeviceRevealUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceViewOnceOpenJob.class);

  private static final String KEY_MESSAGE_ID = "message_id";

  private SerializableSyncMessageId messageId;

  public MultiDeviceViewOnceOpenJob(SyncMessageId messageId) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId);
  }

  private MultiDeviceViewOnceOpenJob(@NonNull Parameters parameters, @NonNull SyncMessageId syncMessageId) {
    super(parameters);
    this.messageId = new SerializableSyncMessageId(syncMessageId.getAddress().toPhoneString(), syncMessageId.getTimetamp());
  }

  @Override
  public @NonNull Data serialize() {
    String serialized;

    try {
      serialized = JsonUtils.toJson(messageId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    return new Data.Builder().putString(KEY_MESSAGE_ID, serialized).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device...");
      return;
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    ViewOnceOpenMessage        openMessage  = new ViewOnceOpenMessage(messageId.sender, messageId.timestamp);

    messageSender.sendMessage(SignalServiceSyncMessage.forViewOnceOpen(openMessage), UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {

  }

  private static class SerializableSyncMessageId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    private final String sender;

    @JsonProperty
    private final long   timestamp;

    private SerializableSyncMessageId(@JsonProperty("sender") String sender, @JsonProperty("timestamp") long timestamp) {
      this.sender = sender;
      this.timestamp = timestamp;
    }
  }

  public static final class Factory implements Job.Factory<MultiDeviceViewOnceOpenJob> {
    @Override
    public @NonNull MultiDeviceViewOnceOpenJob create(@NonNull Parameters parameters, @NonNull Data data) {
      SerializableSyncMessageId messageId;

      try {
        messageId = JsonUtils.fromJson(data.getString(KEY_MESSAGE_ID), SerializableSyncMessageId.class);
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      SyncMessageId syncMessageId = new SyncMessageId(Address.fromSerialized(messageId.sender), messageId.timestamp);

      return new MultiDeviceViewOnceOpenJob(parameters, syncMessageId);
    }
  }
}
