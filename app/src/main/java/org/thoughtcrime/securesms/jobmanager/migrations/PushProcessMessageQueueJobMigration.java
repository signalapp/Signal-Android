package org.thoughtcrime.securesms.jobmanager.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;

import java.io.IOException;

/**
 * We changed the format of the queue key for {@link org.thoughtcrime.securesms.jobs.PushProcessMessageJob}
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
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    if ("PushProcessJob".equals(jobData.getFactoryKey())) {
      Log.i(TAG, "Found a PushProcessMessageJob to migrate.");
      try {
        return migratePushProcessMessageJob(context, jobData);
      } catch (IOException e) {
        Log.w(TAG, "Failed to migrate message job.", e);
        return jobData;
      }
    }
    return jobData;
  }

  private static @NonNull JobData migratePushProcessMessageJob(@NonNull Context context, @NonNull JobData jobData) throws IOException {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    String suffix = "";

    if (data.getInt("message_state") == 0) {
      SignalServiceContent content = SignalServiceContent.deserialize(Base64.decode(data.getString("message_content")));

      if (content != null && content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
        Log.i(TAG, "Migrating a group message.");

        GroupId   groupId   = GroupId.v2(content.getDataMessage().get().getGroupContext().get().getMasterKey());
        Recipient recipient = Recipient.externalGroupExact(groupId);

        suffix = recipient.getId().toQueueKey();
      } else if (content != null) {
        Log.i(TAG, "Migrating an individual message.");
        suffix = RecipientId.from(content.getSender()).toQueueKey();
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
