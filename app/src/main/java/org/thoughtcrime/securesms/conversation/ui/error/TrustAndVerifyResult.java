package org.thoughtcrime.securesms.conversation.ui.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;

/**
 * Result of trust/verify after safety number change.
 */
public class TrustAndVerifyResult {

  private final List<RecipientId> changedRecipients;
  private final MessageRecord     messageRecord;
  private final Result            result;

  static TrustAndVerifyResult trustAndVerify(@NonNull List<ChangedRecipient> changedRecipients) {
    return new TrustAndVerifyResult(changedRecipients, null, Result.TRUST_AND_VERIFY);
  }

  static TrustAndVerifyResult trustVerifyAndResend(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    return new TrustAndVerifyResult(changedRecipients, messageRecord, Result.TRUST_VERIFY_AND_RESEND);
  }

  TrustAndVerifyResult(@NonNull List<ChangedRecipient> changedRecipients, @Nullable MessageRecord messageRecord, @NonNull Result result) {
    this.changedRecipients = Stream.of(changedRecipients).map(changedRecipient -> changedRecipient.getRecipient().getId()).toList();
    this.messageRecord     = messageRecord;
    this.result            = result;
  }

  public @NonNull List<RecipientId> getChangedRecipients() {
    return changedRecipients;
  }

  public @Nullable MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public @NonNull Result getResult() {
    return result;
  }

  public enum Result {
    TRUST_AND_VERIFY,
    TRUST_VERIFY_AND_RESEND,
    UNKNOWN
  }
}
