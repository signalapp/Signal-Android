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

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.InvalidVersionException;
import org.thoughtcrime.securesms.crypto.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity for displaying sent/received session keys.
 * 
 * @author Moxie Marlinspike
 */

public class ReceiveKeyActivity extends Activity {

  private TextView descriptionText;
  private TextView signatureText;
	
  private Button confirmButton;
  private Button cancelButton;
  private Button verifySessionButton;
  private Button verifyIdentityButton;
	
  private Recipient recipient;
  private long      threadId;
	
  private MasterSecret         masterSecret;
  private KeyExchangeMessage   keyExchangeMessage;
  private KeyExchangeProcessor keyExchangeProcessor;
	
  private boolean sent;
	
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
      initializeCorruptedKeyText();
    } catch (InvalidVersionException ive) {
      initializeBadVersionText();
    }
    initializeListeners();
  }
  
  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }
	
  private void initializeText() {
    if (keyExchangeProcessor.hasCompletedSession()) initializeTextForExistingSession();
    else                                            initializeTextForNewSession();
		
    initializeSignatureText();
  }

  private void initializeCorruptedKeyText() {
    descriptionText.setText("ERROR:" + "\n\n" + getString(R.string.you_have_received_a_corrupted_public_key));
    confirmButton.setVisibility(View.GONE);
  }
	
  private void initializeBadVersionText() {
    descriptionText.setText("ERROR:" + "\n\n" + getString(R.string.you_have_received_a_public_key));
    confirmButton.setVisibility(View.GONE);		
  }
	
  private void initializeSignatureText() {
    if (!keyExchangeMessage.hasIdentityKey()) {
      signatureText.setText(R.string.this_key_exchange_message_does_not_include_an_identity_signature_);
      return;
    }
		
    IdentityKey identityKey = keyExchangeMessage.getIdentityKey();
    String identityName     = DatabaseFactory.getIdentityDatabase(this).getNameForIdentity(masterSecret, identityKey);
		
    if (identityName == null) {
      signatureText.setText(R.string.this_key_exchange_message_includes_an_identity_signature_but_you_do_not_yet_trust_it_);
    } else {
      signatureText.setText(getString(R.string.this_key_exchange_message_includes_an_identity_signature_which_you_trust_for_) + identityName);
    }
  }
	
  private void initializeTextForExistingSession() {
    if (keyExchangeProcessor.isRemoteKeyExchangeForExistingSession(keyExchangeMessage)) {
      descriptionText.setText(getString(R.string.this_is_the_key_that_you_sent_to_start_your_current_encrypted_session_with, recipient.toShortString()));
      this.confirmButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.VISIBLE);
    } else if (keyExchangeProcessor.isLocalKeyExchangeForExistingSession(keyExchangeMessage)) {
      descriptionText.setText(getString(R.string.this_is_the_key_that_you_received_to_start_your_current_encrypted_session_with, recipient.toShortString()));
      this.confirmButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.VISIBLE);
    } else {
      descriptionText.setText(getString(R.string.you_have_received_a_key_exchange_message_from,recipient.toShortString()) +
			      "\n\n" + getString(R.string.warning_you_already_have_an_encrypted_session_with_this_contact_)  +
			      getString(R.string._if_you_choose_to_accept_this_key_exchange_message_it_will_destroy_your_) );
      this.confirmButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.GONE);
    }		
  }
	
  private void initializeTextForNewSession() {
    if (keyExchangeProcessor.hasInitiatedSession() && !this.sent)
      descriptionText.setText(getString(R.string.you_have_received_a_key_exchange_message_from, recipient.toShortString()) + 
			      getString(R.string.you_have_previously_initiated_a_session_with_this_contact_and_by_accepting_this_key_you_will_complete_the_key_exchange_) + 
			      getString(R.string.would_you_like_to_complete_this_key_exchange_));
    else if (keyExchangeProcessor.hasInitiatedSession() && this.sent)
      descriptionText.setText(getString(R.string.you_have_initiated_a_key_exchange_message_with,recipient.toShortString()));
    else if (!keyExchangeProcessor.hasInitiatedSession() && !this.sent)
      descriptionText.setText(getString(R.string.you_have_received_a_key_exchange_message_from, recipient.toShortString()) + 
			      getString(R.string.you_have_no_existing_session_with_this_contact_would_you_like_to_complete_this_key_exchange_));
  }
	
  private void initializeKey() throws InvalidKeyException, InvalidVersionException {
    String messageBody      = getIntent().getStringExtra("body");
    this.keyExchangeMessage = new KeyExchangeMessage(messageBody);
  }
	
  private void initializeResources() {
    this.descriptionText      = (TextView) findViewById(R.id.description_text);
    this.signatureText        = (TextView) findViewById(R.id.signature_text);
    this.confirmButton        = (Button)   findViewById(R.id.ok_button);
    this.cancelButton         = (Button)   findViewById(R.id.cancel_button);
    this.verifyIdentityButton = (Button)findViewById(R.id.verify_identity_button);
    this.verifySessionButton  = (Button)findViewById(R.id.verify_session_button);
    this.recipient            = getIntent().getParcelableExtra("recipient");
    this.threadId             = getIntent().getLongExtra("thread_id", -1);
    this.masterSecret         = (MasterSecret)getIntent().getParcelableExtra("master_secret");
    this.sent                 = getIntent().getBooleanExtra("sent", false);
    this.keyExchangeProcessor = new KeyExchangeProcessor(this, masterSecret, recipient);
  }
	
  private void initializeListeners() {
    this.confirmButton.setOnClickListener(new OkListener());
    this.cancelButton.setOnClickListener(new CancelListener());
    this.verifyIdentityButton.setOnClickListener(new VerifyIdentityListener());
    this.verifySessionButton.setOnClickListener(new VerifySessionListener());
  }
	
  private class VerifyIdentityListener implements View.OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(ReceiveKeyActivity.this, VerifyIdentityActivity.class);
      intent.putExtra("recipient", recipient);
      intent.putExtra("master_secret", masterSecret);
      startActivity(intent);
      finish();
    }
  }
	
  private class VerifySessionListener implements View.OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(ReceiveKeyActivity.this, VerifyKeysActivity.class);
      intent.putExtra("recipient", recipient);
      intent.putExtra("master_secret", masterSecret);
      startActivity(intent);
      finish();
    }
  }
	
  private class OkListener implements View.OnClickListener {
    public void onClick(View v) {
      keyExchangeProcessor.processKeyExchangeMessage(keyExchangeMessage, threadId);		
      finish();
    }
  }
	
  private class CancelListener implements View.OnClickListener {
    public void onClick(View v) {
      ReceiveKeyActivity.this.finish();
    }
  }

}
