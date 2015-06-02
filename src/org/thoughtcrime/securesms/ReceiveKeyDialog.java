/**
 * Copyright (C) 2011 Whisper Systems
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
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

import java.io.IOException;

/**
 * Activity for displaying sent/received session keys.
 *
 * @author Moxie Marlinspike
 */

public class ReceiveKeyDialog extends MaterialDialog {
  private static final String TAG = ReceiveKeyDialog.class.getSimpleName();

  private ReceiveKeyDialog(Builder builder, MessageRecord messageRecord, IdentityKey identityKey) {
    super(builder);
    initializeText(messageRecord, identityKey);
  }

  public static @NonNull ReceiveKeyDialog build(@NonNull Context context,
                                                @NonNull MasterSecret masterSecret,
                                                @NonNull MessageRecord messageRecord)
  {
    try {
      final IncomingPreKeyBundleMessage message = getMessage(messageRecord);
      final IdentityKey identityKey = getIdentityKey(message);
      Builder builder = new Builder(context).customView(R.layout.receive_key_dialog, true)
                                            .positiveText(R.string.receive_key_dialog__complete)
                                            .negativeText(android.R.string.cancel)
                                            .callback(new ReceiveKeyDialogCallback(context,
                                                                                   masterSecret,
                                                                                   messageRecord,
                                                                                   message,
                                                                                   identityKey));
      return new ReceiveKeyDialog(builder, messageRecord, identityKey);
    } catch (InvalidKeyException | InvalidVersionException | InvalidMessageException | LegacyMessageException e) {
      throw new AssertionError(e);
    }
  }

  private void initializeText(final MessageRecord messageRecord, final IdentityKey identityKey) {
    if (getCustomView() == null) {
      throw new AssertionError("CustomView should not be null in ReceiveKeyDialog.");
    }
    TextView        descriptionText = (TextView) getCustomView().findViewById(R.id.description_text);
    String          introText       = getContext().getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different);
    SpannableString spannableString = new SpannableString(introText + " " +
                                                          getContext().getString(R.string.ReceiveKeyActivity_you_may_wish_to_verify_this_contact));
    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
        intent.putExtra("recipient", messageRecord.getIndividualRecipient().getRecipientId());
        intent.putExtra("remote_identity", new IdentityKeyParcelable(identityKey));
        getContext().startActivity(intent);
      }
    }, introText.length() + 1,
       spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descriptionText.setText(spannableString);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private static IncomingPreKeyBundleMessage getMessage(MessageRecord messageRecord)
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    IncomingTextMessage message = new IncomingTextMessage(messageRecord.getIndividualRecipient().getNumber(),
                                                          messageRecord.getRecipientDeviceId(),
                                                          System.currentTimeMillis(),
                                                          messageRecord.getBody().getBody(),
                                                          Optional.<TextSecureGroup>absent());

    return new IncomingPreKeyBundleMessage(message, message.getMessageBody());
  }

  private static IdentityKey getIdentityKey(IncomingPreKeyBundleMessage message)
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    try {
      return new PreKeyWhisperMessage(Base64.decode(message.getMessageBody())).getIdentityKey();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static class ReceiveKeyDialogCallback extends ButtonCallback {
    private Context                     context;
    private MasterSecret                masterSecret;
    private MessageRecord               messageRecord;
    private IncomingPreKeyBundleMessage message;
    private IdentityKey                 identityKey;

    public ReceiveKeyDialogCallback(Context context,
                                    MasterSecret masterSecret,
                                    MessageRecord messageRecord,
                                    IncomingPreKeyBundleMessage message,
                                    IdentityKey identityKey)
    {
      this.context       = context;
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.message       = message;
      this.identityKey   = identityKey;
    }

    @Override public void onPositive(MaterialDialog dialog) {
      new VerifyAsyncTask(context, masterSecret, messageRecord, message, identityKey).execute();
    }
  }

  private static class VerifyAsyncTask extends ProgressDialogAsyncTask<Void,Void,Void> {

    private MasterSecret                masterSecret;
    private MessageRecord               messageRecord;
    private IncomingPreKeyBundleMessage message;
    private IdentityKey                 identityKey;

    public VerifyAsyncTask(Context context,
                           MasterSecret masterSecret,
                           MessageRecord messageRecord,
                           IncomingPreKeyBundleMessage message,
                           IdentityKey identityKey)
    {
      super(context, R.string.ReceiveKeyActivity_processing, R.string.ReceiveKeyActivity_processing_key_exchange);
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.message       = message;
      this.identityKey   = identityKey;
    }

    @Override
    protected Void doInBackground(Void... params) {
      if (getContext() == null) return null;

      IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());
      PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(getContext());

      identityDatabase.saveIdentity(masterSecret,
                                    messageRecord.getIndividualRecipient().getRecipientId(),
                                    identityKey);
      try {
        byte[] body = Base64.decode(message.getMessageBody());
        TextSecureEnvelope envelope = new TextSecureEnvelope(3, message.getSender(),
                                                             message.getSenderDeviceId(), "",
                                                             message.getSentTimestampMillis(),
                                                             body);

        long pushId = pushDatabase.insert(envelope);

        ApplicationContext.getInstance(getContext())
                          .getJobManager()
                          .add(new PushDecryptJob(getContext(), pushId, messageRecord.getId(), message.getSender()));
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      return null;
    }
  }
}
