package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.sms.MessageSender;

import java.lang.ref.WeakReference;

public class ResendAsyncTask extends AsyncTask<Void, Void, Void> {
  private WeakReference<Context> weakContext;
  private final MasterSecret     masterSecret;
  private final MessageRecord    record;
  private final NetworkFailure   failure;

  public ResendAsyncTask(Context context, MasterSecret masterSecret, MessageRecord record,
                         NetworkFailure failure)
  {
    this.weakContext  = new WeakReference<>(context);
    this.masterSecret = masterSecret;
    this.record       = record;
    this.failure      = failure;
  }

  protected Context getContext() {
    return weakContext.get();
  }

  @Override
  protected Void doInBackground(Void... params) {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());
    mmsDatabase.removeFailure(record.getId(), failure);

    if (record.getRecipients().isGroupRecipient()) {
      MessageSender.resendGroupMessage(getContext(), masterSecret, record, failure.getRecipientId());
    } else {
      MessageSender.resend(getContext(), masterSecret, record);
    }
    return null;
  }
}
