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
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;

import java.io.IOException;

/**
 * Activity for displaying sent/received session keys.
 *
 * @author Moxie Marlinspike
 */

public class ReceiveKeyActivity extends PassphraseRequiredSherlockActivity {

  private TextView descriptionText;

  private Button confirmButton;
  private Button cancelButton;

  private Recipient recipient;
  private long      threadId;
  private long      messageId;

  private MasterSecret         masterSecret;
  private PreKeyBundleMessage  keyExchangeMessageBundle;
  private KeyExchangeMessage   keyExchangeMessage;
  private KeyExchangeProcessor keyExchangeProcessor;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.receive_key_activity);

    initializeResources();

    try {
      initializeKey();
      initializeText();
    } catch (InvalidKeyException ike) {
      Log.w("ReceiveKeyActivity", ike);
    } catch (InvalidVersionException ive) {
      Log.w("ReceiveKeyActivity", ive);
    }
    initializeListeners();
  }

  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  private void initializeText() {
    if (isTrusted(keyExchangeMessage, keyExchangeMessageBundle)) initializeTrustedText();
    else                                                         initializeUntrustedText();
  }

  private void initializeTrustedText() {
    descriptionText.setText(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_trusted_but));
  }

  private void initializeUntrustedText() {
    SpannableString spannableString = new SpannableString(getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different) + " " +
                                                          getString(R.string.ReceiveKeyActivity_you_may_wish_to_verify_this_contact));
    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(ReceiveKeyActivity.this, VerifyIdentityActivity.class);
        intent.putExtra("recipient", recipient);
        intent.putExtra("master_secret", masterSecret);
        intent.putExtra("remote_identity",
                        keyExchangeMessage == null ?
                            keyExchangeMessageBundle.getIdentityKey() : keyExchangeMessage.getIdentityKey());
        startActivity(intent);
      }
    }, getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different).length() +1,
       spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descriptionText.setText(spannableString);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private boolean isTrusted(KeyExchangeMessage message, PreKeyBundleMessage messageBundle) {
    return (message != null && keyExchangeProcessor.isTrusted(message)) ||
           (messageBundle != null && keyExchangeProcessor.isTrusted(messageBundle));
  }

  private void initializeKey() throws InvalidKeyException, InvalidVersionException {
    try {
      String messageBody = getIntent().getStringExtra("body");

      if (getIntent().getBooleanExtra("is_bundle", false)) {
        SmsTransportDetails transportDetails = new SmsTransportDetails();
        byte[]              body             = transportDetails.getDecodedMessage(messageBody.getBytes());

        this.keyExchangeMessageBundle = new PreKeyBundleMessage(body);
      } else {
        this.keyExchangeMessage = new KeyExchangeMessage(messageBody);
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private void initializeResources() {
    this.descriptionText      = (TextView) findViewById(R.id.description_text);
    this.confirmButton        = (Button)   findViewById(R.id.ok_button);
    this.cancelButton         = (Button)   findViewById(R.id.cancel_button);
    this.recipient            = getIntent().getParcelableExtra("recipient");
    this.threadId             = getIntent().getLongExtra("thread_id", -1);
    this.messageId            = getIntent().getLongExtra("message_id", -1);
    this.masterSecret         = getIntent().getParcelableExtra("master_secret");
    this.keyExchangeProcessor = new KeyExchangeProcessor(this, masterSecret, recipient);
  }

  private void initializeListeners() {
    this.confirmButton.setOnClickListener(new OkListener());
    this.cancelButton.setOnClickListener(new CancelListener());
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
          if (keyExchangeMessage != null) {
            keyExchangeProcessor.processKeyExchangeMessage(keyExchangeMessage, threadId);
            DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                           .markAsProcessedKeyExchange(messageId);
          } else if (keyExchangeMessageBundle != null) {
            try {
              keyExchangeProcessor.processKeyExchangeMessage(keyExchangeMessageBundle);
              byte[]              bundledMessage   = keyExchangeMessageBundle.getBundledMessage();
              SmsTransportDetails transportDetails = new SmsTransportDetails();
              String              messageBody      = new String(transportDetails.getEncodedMessage(bundledMessage));

              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .updateBundleMessageBody(masterSecret, messageId, messageBody);

              DecryptingQueue.scheduleDecryption(ReceiveKeyActivity.this, masterSecret, messageId,
                                                 threadId, recipient.getNumber(), messageBody,
                                                 true, false);
            } catch (InvalidKeyIdException e) {
              Log.w("ReceiveKeyActivity", e);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsCorruptKeyExchange(messageId);
            }
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
