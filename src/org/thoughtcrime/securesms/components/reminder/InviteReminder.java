package org.thoughtcrime.securesms.components.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

public class InviteReminder extends Reminder {

  @SuppressLint("StaticFieldLeak")
  public InviteReminder(final @NonNull Context context,
                        final @NonNull Recipient recipient)
  {
    super(context.getString(R.string.reminder_header_invite_title),
          context.getString(R.string.reminder_header_invite_text, recipient.toShortString()));

    setDismissListener(v -> SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(context).setSeenInviteReminder(recipient.getId(), true);
    }));
  }
}
