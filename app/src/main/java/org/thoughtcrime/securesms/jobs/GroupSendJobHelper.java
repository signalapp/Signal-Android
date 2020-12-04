package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class GroupSendJobHelper {

  private static final String TAG = Log.tag(GroupSendJobHelper.class);

  private GroupSendJobHelper() {
  }

  static List<Recipient> getCompletedSends(@NonNull Context context, @NonNull Collection<SendMessageResult> results) {
    List<Recipient> completions = new ArrayList<>(results.size());

    for (SendMessageResult sendMessageResult : results) {
      Recipient recipient = Recipient.externalPush(context, sendMessageResult.getAddress());

      if (sendMessageResult.getIdentityFailure() != null) {
        Log.w(TAG, "Identity failure for " + recipient.getId());
      }

      if (sendMessageResult.isUnregisteredFailure()) {
        Log.w(TAG, "Unregistered failure for " + recipient.getId());
      }

      if (sendMessageResult.getSuccess()         != null ||
          sendMessageResult.getIdentityFailure() != null ||
          sendMessageResult.isUnregisteredFailure())
      {
        completions.add(recipient);
      }
    }

    return completions;
  }
}
