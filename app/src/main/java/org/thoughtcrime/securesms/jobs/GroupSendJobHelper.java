package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GroupSendJobHelper {

  private static final String TAG = Log.tag(GroupSendJobHelper.class);

  private GroupSendJobHelper() {
  }

  public static @NonNull SendResult getCompletedSends(@NonNull List<Recipient> possibleRecipients, @NonNull Collection<SendMessageResult> results) {
    RecipientAccessList accessList   = new RecipientAccessList(possibleRecipients);
    List<Recipient>     completions  = new ArrayList<>(results.size());
    List<RecipientId>   skipped      = new ArrayList<>();
    List<RecipientId>   unregistered = new ArrayList<>();

    for (SendMessageResult sendMessageResult : results) {
      Recipient recipient = accessList.requireByAddress(sendMessageResult.getAddress());

      if (sendMessageResult.getIdentityFailure() != null) {
        Log.w(TAG, "Identity failure for " + recipient.getId());
      }

      if (sendMessageResult.isUnregisteredFailure()) {
        Log.w(TAG, "Unregistered failure for " + recipient.getId());
        skipped.add(recipient.getId());
        unregistered.add(recipient.getId());
      }

      if (sendMessageResult.getProofRequiredFailure() != null) {
        Log.w(TAG, "Proof required failure for " + recipient.getId());
        skipped.add(recipient.getId());
      }

      if (sendMessageResult.isInvalidPreKeyFailure()) {
        Log.w(TAG, "Invalid pre-key failure for " + recipient.getId());
        skipped.add(recipient.getId());
      }

      if (sendMessageResult.isCanceledFailure()) {
        Log.w(TAG, "Canceled result " + recipient.getId());
        skipped.add(recipient.getId());
      }

      if (sendMessageResult.getSuccess() != null ||
          sendMessageResult.getIdentityFailure() != null ||
          sendMessageResult.getProofRequiredFailure() != null ||
          sendMessageResult.isUnregisteredFailure() ||
          sendMessageResult.isInvalidPreKeyFailure())
      {
        completions.add(recipient);
      }
    }

    return new SendResult(completions, skipped, unregistered);
  }

  public static class SendResult {
    /** Recipients that do not need to be sent to again. Includes certain types of non-retryable failures. Important: items in this list can overlap with other lists in the result. */
    public final List<Recipient>   completed;

    /** Recipients that were not sent to and can be shown as "skipped" in the UI. Important: items in this list can overlap with other lists in the result. */
    public final List<RecipientId> skipped;

    /** Recipients that were discovered to be unregistered. Important: items in this list can overlap with other lists in the result. */
    public final List<RecipientId> unregistered;

    public SendResult(@NonNull List<Recipient> completed, @NonNull List<RecipientId> skipped, @NonNull List<RecipientId> unregistered) {
      this.completed    = completed;
      this.skipped      = skipped;
      this.unregistered = unregistered;
    }
  }
}
