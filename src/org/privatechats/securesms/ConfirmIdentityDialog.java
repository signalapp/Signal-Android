package org.privatechats.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import org.privatechats.securesms.crypto.IdentityKeyParcelable;
import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.database.IdentityDatabase;
import org.privatechats.securesms.database.MmsAddressDatabase;
import org.privatechats.securesms.database.MmsDatabase;
import org.privatechats.securesms.database.MmsSmsDatabase;
import org.privatechats.securesms.database.PushDatabase;
import org.privatechats.securesms.database.SmsDatabase;
import org.privatechats.securesms.database.documents.IdentityKeyMismatch;
import org.privatechats.securesms.database.model.MessageRecord;
import org.privatechats.securesms.jobs.PushDecryptJob;
import org.privatechats.securesms.recipients.Recipient;
import org.privatechats.securesms.recipients.RecipientFactory;
import org.privatechats.securesms.recipients.Recipients;
import org.privatechats.securesms.sms.MessageSender;
import org.privatechats.securesms.util.Base64;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.internal.push.TextSecureProtos;

import java.io.IOException;

public class ConfirmIdentityDialog extends AlertDialog {

  private static final String TAG = ConfirmIdentityDialog.class.getSimpleName();

  private OnClickListener callback;

  public ConfirmIdentityDialog(Context context,
                               MasterSecret masterSecret,
                               MessageRecord messageRecord,
                               IdentityKeyMismatch mismatch)
  {
    super(context);
    Recipient       recipient       = RecipientFactory.getRecipientForId(context, mismatch.getRecipientId(), false);
    String          name            = recipient.toShortString();
    String          introduction    = String.format(context.getString(R.string.ConfirmIdentityDialog_the_signature_on_this_key_exchange_is_different), name, name);
    SpannableString spannableString = new SpannableString(introduction + " " +
                                                          context.getString(R.string.ConfirmIdentityDialog_you_may_wish_to_verify_this_contact));

    spannableString.setSpan(new VerifySpan(context, mismatch),
                            introduction.length()+1, spannableString.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    setTitle(name);
    setMessage(spannableString);

    setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.ConfirmIdentityDialog_accept), new AcceptListener(masterSecret, messageRecord, mismatch));
    setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),               new CancelListener());
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MasterSecret        masterSecret;
    private final MessageRecord       messageRecord;
    private final IdentityKeyMismatch mismatch;

    private AcceptListener(MasterSecret masterSecret, MessageRecord messageRecord, IdentityKeyMismatch mismatch) {
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.mismatch      = mismatch;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>()
      {
        @Override
        protected Void doInBackground(Void... params) {
          IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());

          identityDatabase.saveIdentity(mismatch.getRecipientId(),
                                        mismatch.getIdentityKey());

          processMessageRecord(messageRecord);
          processPendingMessageRecords(messageRecord.getThreadId(), mismatch);

          return null;
        }

        private void processMessageRecord(MessageRecord messageRecord) {
          if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
          else                            processIncomingMessageRecord(messageRecord);
        }

        private void processPendingMessageRecords(long threadId, IdentityKeyMismatch mismatch) {
          MmsSmsDatabase        mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(getContext());
          Cursor                cursor         = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
          MmsSmsDatabase.Reader reader         = mmsSmsDatabase.readerFor(cursor, masterSecret);
          MessageRecord         record;

          try {
            while ((record = reader.getNext()) != null) {
              for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
                if (mismatch.equals(recordMismatch)) {
                  processMessageRecord(record);
                }
              }
            }
          } finally {
            if (reader != null)
              reader.close();
          }
        }

        private void processOutgoingMessageRecord(MessageRecord messageRecord) {
          SmsDatabase        smsDatabase        = DatabaseFactory.getSmsDatabase(getContext());
          MmsDatabase        mmsDatabase        = DatabaseFactory.getMmsDatabase(getContext());
          MmsAddressDatabase mmsAddressDatabase = DatabaseFactory.getMmsAddressDatabase(getContext());

          if (messageRecord.isMms()) {
            mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getRecipientId(),
                                                 mismatch.getIdentityKey());

            Recipients recipients = mmsAddressDatabase.getRecipientsForId(messageRecord.getId());

            if (recipients.isGroupRecipient()) MessageSender.resendGroupMessage(getContext(), masterSecret, messageRecord, mismatch.getRecipientId());
            else                               MessageSender.resend(getContext(), masterSecret, messageRecord);
          } else {
            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getRecipientId(),
                                                 mismatch.getIdentityKey());

            MessageSender.resend(getContext(), masterSecret, messageRecord);
          }
        }

        private void processIncomingMessageRecord(MessageRecord messageRecord) {
          try {
            PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(getContext());
            SmsDatabase  smsDatabase  = DatabaseFactory.getSmsDatabase(getContext());

            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getRecipientId(),
                                                 mismatch.getIdentityKey());

            TextSecureEnvelope envelope = new TextSecureEnvelope(TextSecureProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
                                                                 messageRecord.getIndividualRecipient().getNumber(),
                                                                 messageRecord.getRecipientDeviceId(), "",
                                                                 messageRecord.getDateSent(),
                                                                 Base64.decode(messageRecord.getBody().getBody()),
                                                                 null);

            long pushId = pushDatabase.insert(envelope);

            ApplicationContext.getInstance(getContext())
                              .getJobManager()
                              .add(new PushDecryptJob(getContext(), pushId, messageRecord.getId(),
                                                      messageRecord.getIndividualRecipient().getNumber()));
          } catch (IOException e) {
            throw new AssertionError(e);
          }
        }

      }.execute();

      if (callback != null) callback.onClick(null, 0);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

  private static class VerifySpan extends ClickableSpan {
    private final Context             context;
    private final IdentityKeyMismatch mismatch;

    private VerifySpan(Context context, IdentityKeyMismatch mismatch) {
      this.context  = context;
      this.mismatch = mismatch;
    }

    @Override
    public void onClick(View widget) {
      Intent intent = new Intent(context, VerifyIdentityActivity.class);
      intent.putExtra("recipient", mismatch.getRecipientId());
      intent.putExtra("remote_identity", new IdentityKeyParcelable(mismatch.getIdentityKey()));
      context.startActivity(intent);
    }
  }

}
