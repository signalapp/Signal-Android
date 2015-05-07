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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
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

public class ReceiveKeyActivity extends PassphraseRequiredActionBarActivity {

  private TextView descriptionText;

  private Button confirmButton;
  private Button cancelButton;

  private Recipient recipient;
  private int       recipientDeviceId;
  private long      messageId;

  private MasterSecret                masterSecret;
  private IncomingPreKeyBundleMessage message;
  private IdentityKey                 identityKey;

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setContentView(R.layout.receive_key_activity);

    initializeResources();

    try {
      initializeKey();
      initializeText();
    } catch (InvalidKeyException | InvalidVersionException | InvalidMessageException | LegacyMessageException ike) {
      Log.w("ReceiveKeyActivity", ike);
    }
    initializeListeners();
  }

  private void initializeText() {
    SpannableString spannableString = new SpannableString(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different) + " " +
                                                          getString(R.string.ReceiveKeyActivity_you_may_wish_to_verify_this_contact));
    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(ReceiveKeyActivity.this, VerifyIdentityActivity.class);
        intent.putExtra("recipient", recipient.getRecipientId());
        intent.putExtra("remote_identity", new IdentityKeyParcelable(identityKey));
        startActivity(intent);
      }
    }, getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different).length() +1,
       spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descriptionText.setText(spannableString);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void initializeKey()
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    IncomingTextMessage message = new IncomingTextMessage(recipient.getNumber(),
                                                          recipientDeviceId,
                                                          System.currentTimeMillis(),
                                                          getIntent().getStringExtra("body"),
                                                          Optional.<TextSecureGroup>absent());

    this.message     = new IncomingPreKeyBundleMessage(message, message.getMessageBody());
    this.identityKey = getIdentityKey(this.message);
  }

  private void initializeResources() {
    this.descriptionText      = (TextView) findViewById(R.id.description_text);
    this.confirmButton        = (Button)   findViewById(R.id.ok_button);
    this.cancelButton         = (Button)   findViewById(R.id.cancel_button);
    this.recipient            = RecipientFactory.getRecipientForId(this, getIntent().getLongExtra("recipient", -1), true);
    this.recipientDeviceId    = getIntent().getIntExtra("recipient_device_id", -1);
    this.messageId            = getIntent().getLongExtra("message_id", -1);
  }

  private void initializeListeners() {
    this.confirmButton.setOnClickListener(new OkListener());
    this.cancelButton.setOnClickListener(new CancelListener());
  }

  private IdentityKey getIdentityKey(IncomingPreKeyBundleMessage message)
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    try {
      return new PreKeyWhisperMessage(Base64.decode(message.getMessageBody())).getIdentityKey();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private class OkListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      new AsyncTask<Void, Void, Void> () {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
          dialog = ProgressDialog.show(ReceiveKeyActivity.this,
                                       getString(R.string.ReceiveKeyActivity_processing),
                                       getString(R.string.ReceiveKeyActivity_processing_key_exchange),
                                       true);
        }

        @Override
        protected Void doInBackground(Void... params) {
          Context          context          = ReceiveKeyActivity.this;
          IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
          PushDatabase     pushDatabase     = DatabaseFactory.getPushDatabase(context);

          identityDatabase.saveIdentity(masterSecret, recipient.getRecipientId(), identityKey);
          try {
            byte[]             body     = Base64.decode(message.getMessageBody());
            TextSecureEnvelope envelope = new TextSecureEnvelope(3, message.getSender(),
                                                                 message.getSenderDeviceId(), "",
                                                                 message.getSentTimestampMillis(),
                                                                 body);

            long pushId = pushDatabase.insert(envelope);

            ApplicationContext.getInstance(context)
                              .getJobManager()
                              .add(new PushDecryptJob(context, pushId, messageId, message.getSender()));
          } catch (IOException e) {
            throw new AssertionError(e);
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          dialog.dismiss();
          finish();
        }
      }.execute();
    }
  }

  private class CancelListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      ReceiveKeyActivity.this.finish();
    }
  }
}
