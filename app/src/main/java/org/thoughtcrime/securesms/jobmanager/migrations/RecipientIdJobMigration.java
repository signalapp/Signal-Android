package org.thoughtcrime.securesms.jobmanager.migrations;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.io.Serializable;

public class RecipientIdJobMigration extends JobMigration {

  private final Application application;

  public RecipientIdJobMigration(@NonNull Application application) {
    super(2);
    this.application = application;
  }

  @Override
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    switch(jobData.getFactoryKey()) {
      case "MultiDeviceContactUpdateJob":  return migrateMultiDeviceContactUpdateJob(jobData);
      case "MultiDeviceRevealUpdateJob":   return migrateMultiDeviceViewOnceOpenJob(jobData);
      case "RequestGroupInfoJob":          return migrateRequestGroupInfoJob(jobData);
      case "SendDeliveryReceiptJob":       return migrateSendDeliveryReceiptJob(jobData);
      case "MultiDeviceVerifiedUpdateJob": return migrateMultiDeviceVerifiedUpdateJob(jobData);
      case "RetrieveProfileJob":           return migrateRetrieveProfileJob(jobData);
      case "PushGroupSendJob":             return migratePushGroupSendJob(jobData);
      case "PushGroupUpdateJob":           return migratePushGroupUpdateJob(jobData);
      case "DirectoryRefreshJob":          return migrateDirectoryRefreshJob(jobData);
      case "RetrieveProfileAvatarJob":     return migrateRetrieveProfileAvatarJob(jobData);
      case "MultiDeviceReadUpdateJob":     return migrateMultiDeviceReadUpdateJob(jobData);
      case "PushTextSendJob":              return migratePushTextSendJob(jobData);
      case "PushMediaSendJob":             return migratePushMediaSendJob(jobData);
      case "SmsSendJob":                   return migrateSmsSendJob(jobData);
      default:                             return jobData;
    }
  }

  private @NonNull JobData migrateMultiDeviceContactUpdateJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String      address     = data.hasString("address") ? data.getString("address") : null;
    JsonJobData updatedData = new JsonJobData.Builder().putString("recipient", address != null ? Recipient.external(application, address).getId().serialize() : null)
                                                       .putBoolean("force_sync", data.getBoolean("force_sync"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateMultiDeviceViewOnceOpenJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    try {
      String                       rawOld      = data.getString("message_id");
      OldSerializableSyncMessageId old         = JsonUtils.fromJson(rawOld, OldSerializableSyncMessageId.class);
      Recipient                    recipient   = Recipient.external(application, old.sender);
      NewSerializableSyncMessageId updated     = new NewSerializableSyncMessageId(recipient.getId().serialize(), old.timestamp);
      String      rawUpdated  = JsonUtils.toJson(updated);
      JsonJobData updatedData = new JsonJobData.Builder().putString("message_id", rawUpdated).build();

      return jobData.withData(updatedData.serialize());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private @NonNull JobData migrateRequestGroupInfoJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String    address     = data.getString("source");
    Recipient recipient   = Recipient.external(application, address);
    JsonJobData updatedData = new JsonJobData.Builder().putString("source", recipient.getId().serialize())
                                                       .putString("group_id", data.getString("group_id"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateSendDeliveryReceiptJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String    address     = data.getString("address");
    Recipient recipient   = Recipient.external(application, address);
    JsonJobData updatedData = new JsonJobData.Builder().putString("recipient", recipient.getId().serialize())
                                                       .putLong("message_id", data.getLong("message_id"))
                                                       .putLong("timestamp", data.getLong("timestamp"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateMultiDeviceVerifiedUpdateJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String    address     = data.getString("destination");
    Recipient recipient   = Recipient.external(application, address);
    JsonJobData updatedData = new JsonJobData.Builder().putString("destination", recipient.getId().serialize())
                                                       .putString("identity_key", data.getString("identity_key"))
                                                       .putInt("verified_status", data.getInt("verified_status"))
                                                       .putLong("timestamp", data.getLong("timestamp"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateRetrieveProfileJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String      address     = data.getString("address");
    Recipient   recipient   = Recipient.external(application, address);
    JsonJobData updatedData = new JsonJobData.Builder().putString("recipient", recipient.getId().serialize()).build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migratePushGroupSendJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    // noinspection ConstantConditions
    Recipient   queueRecipient = Recipient.external(application, jobData.getQueueKey());
    String      address        = data.hasString("filter_address") ? data.getString("filter_address") : null;
    RecipientId recipientId    = address != null ? Recipient.external(application, address).getId() : null;
    JsonJobData updatedData    = new JsonJobData.Builder().putString("filter_recipient", recipientId != null ? recipientId.serialize() : null)
                                                          .putLong("message_id", data.getLong("message_id"))
                                                          .build();

    return jobData.withQueueKey(queueRecipient.getId().toQueueKey())
                  .withData(updatedData.serialize());
  }

  private @NonNull JobData migratePushGroupUpdateJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String    address     = data.getString("source");
    Recipient recipient   = Recipient.external(application, address);
    JsonJobData updatedData = new JsonJobData.Builder().putString("source", recipient.getId().serialize())
                                                       .putString("group_id", data.getString("group_id"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateDirectoryRefreshJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String    address     = data.hasString("address") ? data.getString("address") : null;
    Recipient recipient   = address != null ? Recipient.external(application, address) : null;
    JsonJobData updatedData = new JsonJobData.Builder().putString("recipient", recipient != null ? recipient.getId().serialize() : null)
                                                       .putBoolean("notify_of_new_users", data.getBoolean("notify_of_new_users"))
                                                       .build();

    return jobData.withData(updatedData.serialize());
  }

  private @NonNull JobData migrateRetrieveProfileAvatarJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    //noinspection ConstantConditions
    String    queueAddress   = jobData.getQueueKey().substring("RetrieveProfileAvatarJob".length());
    Recipient queueRecipient = Recipient.external(application, queueAddress);
    String    address        = data.getString("address");
    Recipient recipient      = Recipient.external(application, address);
    JsonJobData updatedData    = new JsonJobData.Builder().putString("recipient", recipient.getId().serialize())
                                                          .putString("profile_avatar", data.getString("profile_avatar"))
                                                          .build();

    return jobData.withQueueKey("RetrieveProfileAvatarJob::" + queueRecipient.getId().toQueueKey())
                  .withData(updatedData.serialize());
  }

  private @NonNull JobData migrateMultiDeviceReadUpdateJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    try {
      String[] rawOld     = data.getStringArray("message_ids");
      String[] rawUpdated = new String[rawOld.length];

      for (int i = 0; i < rawOld.length; i++) {
        OldSerializableSyncMessageId old       = JsonUtils.fromJson(rawOld[i], OldSerializableSyncMessageId.class);
        Recipient                    recipient = Recipient.external(application, old.sender);
        NewSerializableSyncMessageId updated   = new NewSerializableSyncMessageId(recipient.getId().serialize(), old.timestamp);

        rawUpdated[i] = JsonUtils.toJson(updated);
      }

      JsonJobData updatedData = new JsonJobData.Builder().putStringArray("message_ids", rawUpdated).build();

      return jobData.withData(updatedData.serialize());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private @NonNull JobData migratePushTextSendJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    //noinspection ConstantConditions
    Recipient recipient = Recipient.external(application, jobData.getQueueKey());
    return jobData.withQueueKey(recipient.getId().toQueueKey());
  }

  private @NonNull JobData migratePushMediaSendJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    //noinspection ConstantConditions
    Recipient recipient = Recipient.external(application, jobData.getQueueKey());
    return jobData.withQueueKey(recipient.getId().toQueueKey());
  }

  private @NonNull JobData migrateSmsSendJob(@NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    //noinspection ConstantConditions
    if (jobData.getQueueKey() != null) {
      Recipient recipient = Recipient.external(application, jobData.getQueueKey());
      return jobData.withQueueKey(recipient.getId().toQueueKey());
    } else {
      return jobData;
    }
  }

  @VisibleForTesting
  static class OldSerializableSyncMessageId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    private final String sender;
    @JsonProperty
    private final long   timestamp;

    OldSerializableSyncMessageId(@JsonProperty("sender") String sender, @JsonProperty("timestamp") long timestamp) {
      this.sender    = sender;
      this.timestamp = timestamp;
    }
  }

  @VisibleForTesting
  static class NewSerializableSyncMessageId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    private final String recipientId;
    @JsonProperty
    private final long   timestamp;

    NewSerializableSyncMessageId(@JsonProperty("recipientId") String recipientId, @JsonProperty("timestamp") long timestamp) {
      this.recipientId = recipientId;
      this.timestamp   = timestamp;
    }
  }
}
