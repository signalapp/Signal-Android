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

import android.app.Activity;
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
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessorV2;
import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.crypto.protocol.PreKeyWhisperMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;

import static org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal.Type;

/**
 * Activity for displaying sent/received session keys.
 *
 * @author Moxie Marlinspike
 */

public class ReceiveKeyActivity extends Activity {

  private TextView descriptionText;

  private Button confirmButton;
  private Button cancelButton;

  private Recipient recipient;
  private int       recipientDeviceId;
  private long      threadId;
  private long      messageId;

  private MasterSecret         masterSecret;
  private PreKeyWhisperMessage keyExchangeMessageBundle;
  private KeyExchangeMessage   keyExchangeMessage;
  private IdentityKey          identityUpdateMessage;

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
    } catch (InvalidMessageException e) {
      Log.w("ReceiveKeyActivity", e);
    }
    initializeListeners();
  }

  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  private void initializeText() {
    if (isTrusted(keyExchangeMessage, keyExchangeMessageBundle, identityUpdateMessage)) {
      initializeTrustedText();
    } else {
      initializeUntrustedText();
    }
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
        IdentityKey remoteIdentity;

        if      (identityUpdateMessage != null)    remoteIdentity = identityUpdateMessage;
        else if (keyExchangeMessageBundle != null) remoteIdentity = keyExchangeMessageBundle.getIdentityKey();
        else                                       remoteIdentity = keyExchangeMessage.getIdentityKey();

        Intent intent = new Intent(ReceiveKeyActivity.this, VerifyIdentityActivity.class);
        intent.putExtra("recipient", recipient);
        intent.putExtra("master_secret", masterSecret);
        intent.putExtra("remote_identity", remoteIdentity);
        startActivity(intent);
      }
    }, getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different).length() +1,
       spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descriptionText.setText(spannableString);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private boolean isTrusted(KeyExchangeMessage message, PreKeyWhisperMessage messageBundle, IdentityKey identityUpdateMessage) {
    RecipientDevice recipientDevice = new RecipientDevice(recipient.getRecipientId(), recipientDeviceId);

    if (message != null) {
      KeyExchangeProcessor processor = KeyExchangeProcessor.createFor(this, masterSecret,
                                                                      recipientDevice, message);
      return processor.isTrusted(message);
    } else if (messageBundle != null) {
      KeyExchangeProcessorV2 processor = new KeyExchangeProcessorV2(this, masterSecret, recipientDevice);
      return processor.isTrusted(messageBundle);
    } else if (identityUpdateMessage != null) {
      KeyExchangeProcessorV2 processor = new KeyExchangeProcessorV2(this, masterSecret, recipientDevice);
      return processor.isTrusted(identityUpdateMessage);
    }

    return false;
  }

  private void initializeKey()
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException
  {
    try {
      String messageBody = getIntent().getStringExtra("body");

      if (getIntent().getBooleanExtra("is_bundle", false)) {
        boolean isPush = getIntent().getBooleanExtra("is_push", false);
        byte[] body;

        if (isPush) {
          body = Base64.decode(messageBody.getBytes());
        } else {
          body = new SmsTransportDetails().getDecodedMessage(messageBody.getBytes());
        }

        this.keyExchangeMessageBundle = new PreKeyWhisperMessage(body);
      } else if (getIntent().getBooleanExtra("is_identity_update", false)) {
        this.identityUpdateMessage = new IdentityKey(Base64.decodeWithoutPadding(messageBody), 0);
      } else {
        this.keyExchangeMessage = KeyExchangeMessage.createFor(messageBody);
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
    this.recipientDeviceId    = getIntent().getIntExtra("recipient_device_id", -1);
    this.threadId             = getIntent().getLongExtra("thread_id", -1);
    this.messageId            = getIntent().getLongExtra("message_id", -1);
    this.masterSecret         = getIntent().getParcelableExtra("master_secret");
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
            try {
              RecipientDevice recipientDevice = new RecipientDevice(recipient.getRecipientId(), recipientDeviceId);
              KeyExchangeProcessor processor = KeyExchangeProcessor.createFor(ReceiveKeyActivity.this, masterSecret, recipientDevice, keyExchangeMessage);
              processor.processKeyExchangeMessage(keyExchangeMessage, threadId);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsProcessedKeyExchange(messageId);
            } catch (InvalidMessageException e) {
              Log.w("ReceiveKeyActivity", e);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsCorruptKeyExchange(messageId);
            }
          } else if (keyExchangeMessageBundle != null) {
            try {
              RecipientDevice recipientDevice = new RecipientDevice(recipient.getRecipientId(), recipientDeviceId);
              KeyExchangeProcessorV2 processor = new KeyExchangeProcessorV2(ReceiveKeyActivity.this,
                                                                            masterSecret, recipientDevice);
              processor.processKeyExchangeMessage(keyExchangeMessageBundle);

              CiphertextMessage bundledMessage = keyExchangeMessageBundle.getWhisperMessage();

              if (getIntent().getBooleanExtra("is_push", false)) {
                String source = Util.canonicalizeNumber(ReceiveKeyActivity.this, recipient.getNumber());
                IncomingPushMessage incoming = new IncomingPushMessage(Type.CIPHERTEXT_VALUE, source, recipientDeviceId, bundledMessage.serialize(), System.currentTimeMillis());

                DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                               .markAsProcessedKeyExchange(messageId);

                Intent intent = new Intent(ReceiveKeyActivity.this, SendReceiveService.class);
                intent.setAction(SendReceiveService.RECEIVE_PUSH_ACTION);
                intent.putExtra("message", incoming);
                startService(intent);
              } else {
                SmsTransportDetails transportDetails = new SmsTransportDetails();
                String              messageBody      = new String(transportDetails.getEncodedMessage(bundledMessage.serialize()));

                DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                               .updateBundleMessageBody(masterSecret, messageId, messageBody);

                DecryptingQueue.scheduleDecryption(ReceiveKeyActivity.this, masterSecret, messageId,
                                                   threadId, recipient.getNumber(), recipientDeviceId,
                                                   messageBody, true, false, false);
              }
            } catch (InvalidKeyIdException e) {
              Log.w("ReceiveKeyActivity", e);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsCorruptKeyExchange(messageId);
            } catch (InvalidKeyException e) {
              Log.w("ReceiveKeyActivity", e);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsCorruptKeyExchange(messageId);
            } catch (InvalidNumberException e) {
              Log.w("ReceiveKeyActivity", e);
              DatabaseFactory.getEncryptingSmsDatabase(ReceiveKeyActivity.this)
                             .markAsCorruptKeyExchange(messageId);
            }
          } else if (identityUpdateMessage != null) {
            DatabaseFactory.getIdentityDatabase(ReceiveKeyActivity.this)
                           .saveIdentity(masterSecret, recipient.getRecipientId(), identityUpdateMessage);

            DatabaseFactory.getSmsDatabase(ReceiveKeyActivity.this).markAsProcessedKeyExchange(messageId);
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
