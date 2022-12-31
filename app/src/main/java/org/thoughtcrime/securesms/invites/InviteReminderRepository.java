package org.thoughtcrime.securesms.invites;

import android.content.Context;

import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

public final class InviteReminderRepository implements InviteReminderModel.Repository {

  private final Context context;

  public InviteReminderRepository(Context context) {
    this.context = context;
  }

  @Override
  public void setHasSeenFirstInviteReminder(Recipient recipient) {
    RecipientTable recipientTable = SignalDatabase.recipients();
    recipientTable.setSeenFirstInviteReminder(recipient.getId());
  }

  @Override
  public void setHasSeenSecondInviteReminder(Recipient recipient) {
    RecipientTable recipientTable = SignalDatabase.recipients();
    recipientTable.setSeenSecondInviteReminder(recipient.getId());
  }

  @Override
  public int getPercentOfInsecureMessages(int insecureCount) {
    MessageTable messageTable = SignalDatabase.messages();
    int          insecure     = messageTable.getInsecureMessageCountForInsights();
    int          secure       = messageTable.getSecureMessageCountForInsights();

    if (insecure + secure == 0) return 0;
    return Math.round(100f * (insecureCount / (float) (insecure + secure)));
  }
}
