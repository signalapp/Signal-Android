package org.thoughtcrime.securesms.jobmanager.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;

import java.io.IOException;
import java.util.Optional;

/**
 * We changed the format of the queue key for legacy PushProcessMessageJob
 * to have the recipient ID in it, so this migrates existing jobs to be in that format.
 */
public class PushProcessMessageQueueJobMigration extends JobMigration {

  private static final String TAG = Log.tag(PushProcessMessageQueueJobMigration.class);

  private final Context context;

  public PushProcessMessageQueueJobMigration(@NonNull Context context) {
    super(6);
    this.context = context;
  }

  @Override
  public @NonNull JobData migrate(@NonNull JobData jobData) {
    if ("PushProcessJob".equals(jobData.getFactoryKey())) {
      Log.i(TAG, "Found a PushProcessMessageJob to migrate.");
      try {
        return migratePushProcessMessageJob(context, jobData);
      } catch (IOException | InvalidInputException e) {
        Log.w(TAG, "Failed to migrate message job.", e);
        return jobData;
      }
    }
    return jobData;
  }

  private static @NonNull JobData migratePushProcessMessageJob(@NonNull Context context, @NonNull JobData jobData) throws IOException, InvalidInputException {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String suffix = "";

    if (data.getInt("message_state") == 0) {
      SignalServiceContentProto proto = SignalServiceContentProto.ADAPTER.decode(Base64.decode(data.getString("message_content")));

      if (proto != null && proto.content != null && proto.content.dataMessage != null && proto.content.dataMessage.groupV2 != null) {
        Log.i(TAG, "Migrating a group message.");

        GroupId   groupId   = GroupId.v2(new GroupMasterKey(proto.content.dataMessage.groupV2.masterKey.toByteArray()));
        Recipient recipient = Recipient.externalGroupExact(groupId);

        suffix = recipient.getId().toQueueKey();
      } else if (proto != null && proto.metadata != null && proto.metadata.address != null) {
        Log.i(TAG, "Migrating an individual message.");
        ServiceId            senderServiceId = ServiceId.parseOrThrow(proto.metadata.address.uuid);
        String               senderE164      = proto.metadata.address.e164;
        SignalServiceAddress sender          = new SignalServiceAddress(senderServiceId, Optional.ofNullable(senderE164));

        suffix = RecipientId.from(sender).toQueueKey();
      }
    } else {
      Log.i(TAG, "Migrating an exception message.");

      String  exceptionSender = data.getString("exception_sender");
      GroupId exceptionGroup  =  GroupId.parseNullableOrThrow(data.getStringOrDefault("exception_groupId", null));

      if (exceptionGroup != null) {
        suffix = Recipient.externalGroupExact(exceptionGroup).getId().toQueueKey();
      } else if (exceptionSender != null) {
        suffix = Recipient.external(context, exceptionSender).getId().toQueueKey();
      }
    }

    return jobData.withQueueKey("__PUSH_PROCESS_JOB__" + suffix);
  }
}
