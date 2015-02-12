/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;

/**
 * A simple view to show the recipients of an open conversation
 *
 * @author Jake McGinty
 */
public class MessageDetailsRecipient extends RelativeLayout
                                  implements Recipient.RecipientModifiedListener
{
  private final static String TAG = MessageDetailsRecipient.class.getSimpleName();

  private Recipient  recipient;
  private TextView   fromView;
  private TextView   errorDescription;
  private Button     conflictButton;
  private Button     resendButton;
  private ImageView  contactPhotoImage;

  private final Handler handler = new Handler();

  public MessageDetailsRecipient(Context context) {
    super(context);
  }

  public MessageDetailsRecipient(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    this.fromView          = (TextView)  findViewById(R.id.from);
    this.errorDescription  = (TextView)  findViewById(R.id.error_description);
    this.contactPhotoImage = (ImageView) findViewById(R.id.contact_photo_image);
    this.conflictButton    = (Button)    findViewById(R.id.conflict_button);
    this.resendButton      = (Button)    findViewById(R.id.resend_button);
  }

  public void set(final MasterSecret masterSecret, final MessageRecord record, final Recipients recipients, final int position) {
    recipient = recipients.getRecipientsList().get(position);
    recipient.addListener(this);
    fromView.setText(formatFrom(recipient));

    setContactPhoto(recipient);

    boolean hasNetworkFailures = populateNetworkFailures(masterSecret, record);
    boolean hasConflicts       = !hasNetworkFailures && populateConflicts(masterSecret, record);

    resendButton.setVisibility(hasNetworkFailures || hasConflicts ? View.VISIBLE : View.GONE);
    conflictButton.setVisibility(hasConflicts ? View.VISIBLE : View.GONE);
    errorDescription.setVisibility(hasNetworkFailures ? View.VISIBLE : View.GONE);
  }

  private boolean populateNetworkFailures(final MasterSecret masterSecret, final MessageRecord record) {
    boolean applicableFailures = false;
    if (record.hasNetworkFailures()) {
      for (final NetworkFailure failure : record.getNetworkFailures()) {
        if (failure.getRecipientId() == recipient.getRecipientId()) {
          applicableFailures = true;
          errorDescription.setText(R.string.MessageDetailsRecipient_failed_to_send);
          resendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
              new ResendAsyncTask(masterSecret, record, failure).execute();
            }
          });
        }
      }
    }
    return applicableFailures;
  }

  private boolean populateConflicts(final MasterSecret masterSecret, final MessageRecord record) {
    boolean applicableFailures = false;
    if (record.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : record.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId() == recipient.getRecipientId()) {
          applicableFailures = true;
          errorDescription.setText(R.string.MessageDetailsRecipient_new_identity);
          conflictButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
              new ConfirmIdentityDialog(getContext(), masterSecret, record, mismatch).show();
            }
          });
        }
      }
    }
    return applicableFailures;
  }

  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  private void setContactPhoto(final Recipient recipient) {
    if (recipient == null) return;
    contactPhotoImage.setImageBitmap(recipient.getContactPhoto());
  }

  private CharSequence formatFrom(Recipient from) {
    final String fromString;
    final boolean isUnnamedGroup = from.isGroupRecipient() && TextUtils.isEmpty(from.getName());
    if (isUnnamedGroup) {
      fromString = getContext().getString(R.string.ConversationActivity_unnamed_group);
    } else {
      fromString = from.toShortString();
    }
    SpannableStringBuilder builder = new SpannableStringBuilder(fromString);

    final int typeface;
    if (isUnnamedGroup) typeface = Typeface.ITALIC;
    else                typeface = Typeface.NORMAL;

    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

    return builder;
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(formatFrom(recipient));
        setContactPhoto(recipient);
      }
    });
  }

  private class ResendAsyncTask extends AsyncTask<Void,Void,Void> {
    private final MasterSecret   masterSecret;
    private final MessageRecord  record;
    private final NetworkFailure failure;

    public ResendAsyncTask(MasterSecret masterSecret, MessageRecord record, NetworkFailure failure) {
      this.masterSecret = masterSecret;
      this.record       = record;
      this.failure      = failure;
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

}
