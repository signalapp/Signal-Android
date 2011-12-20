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
    descriptionText.setText("ERROR:\n\nYou have received a corrupted public key.  This key can not be processed, please re-initiate a secure session.");
    confirmButton.setVisibility(View.GONE);
  }
	
  private void initializeBadVersionText() {
    descriptionText.setText("ERROR:\n\nYou have received a public key from an unsupported version of the protocol.  This key can not be processed, please re-initiate a secure session.");
    confirmButton.setVisibility(View.GONE);		
  }
	
  private void initializeSignatureText() {
    if (!keyExchangeMessage.hasIdentityKey()) {
      signatureText.setText("This key exchange message does not include an identity signature.");
      return;
    }
		
    IdentityKey identityKey = keyExchangeMessage.getIdentityKey();
    String identityName     = DatabaseFactory.getIdentityDatabase(this).getNameForIdentity(masterSecret, identityKey);
		
    if (identityName == null) {
      signatureText.setText("This key exchange message includes an identity signature, but you do not yet trust it.");
    } else {
      signatureText.setText("This key exchange message includes an identity signature which you trust for: " + identityName);
    }
  }
	
  private void initializeTextForExistingSession() {
    if (keyExchangeProcessor.isRemoteKeyExchangeForExistingSession(keyExchangeMessage)) {
      descriptionText.setText("This is the key that you sent to start your current encrypted session with " + recipient.toShortString());
      this.confirmButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.VISIBLE);
    } else if (keyExchangeProcessor.isLocalKeyExchangeForExistingSession(keyExchangeMessage)) {
      descriptionText.setText("This is the key that you received to start your current encrypted session with " + recipient.toShortString());
      this.confirmButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.VISIBLE);
    } else {
      descriptionText.setText("You have received a Key Exchange message from " + recipient.toShortString() + "." +
			      "\n\nWARNING:  You already have an encrypted session with this contact."  +
			      " If you choose to accept this key exchange message, it will destroy your " +
			      "existing session and you will have to re-authenticate.  Would you like to complete " +
			      "this key exchange?");
      this.confirmButton.setVisibility(View.VISIBLE);
      this.verifyIdentityButton.setVisibility(View.GONE);
      this.verifySessionButton.setVisibility(View.GONE);
    }		
  }
	
  private void initializeTextForNewSession() {
    if (keyExchangeProcessor.hasInitiatedSession() && !this.sent)
      descriptionText.setText("You have received a Key Exchange message from " + recipient.toShortString() + 
			      ".  You have previously initiated a session with this contact, " + 
			      "and by accepting this key you will complete the key exchange.  " + 
			      "Would you like to complete this key exchange?");
    else if (keyExchangeProcessor.hasInitiatedSession() && this.sent)
      descriptionText.setText("You have initiated a Key Exchange message with " + recipient.toShortString() + 
			      " but have not yet received a reply.");
    else if (!keyExchangeProcessor.hasInitiatedSession() && !this.sent)
      descriptionText.setText("You have received a Key Exchange message from " + recipient.toShortString() + 
			      ".  You have no existing session with this contact, would you like " + 
			      "to complete this key exchange?");
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
