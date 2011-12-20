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

import org.thoughtcrime.securesms.components.RecipientsPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.NameAndNumber;
import org.thoughtcrime.securesms.crypto.KeyExchangeInitiator;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Contacts.People;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;

/**
 * Distinct activity for selecting a contact to initiate a key exchange with.
 * Can ordinarily be done through ComposeMessageActivity, this allows for 
 * initiating key exchanges outside of that context.
 * 
 * @author Moxie Marlinspike
 *
 */
public class SendKeyActivity extends Activity {
	
  private static final int PICK_CONTACT = 1;
	
  private MasterSecret masterSecret;
  private RecipientsPanel recipientsPanel;
  private ImageButton addContactButton;
  private Button sendButton;
  private Button cancelButton;
		
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.send_key_activity);
		
    initializeResources();
    initializeDefaults();
  }
  
  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override  
  public void onActivityResult(int reqCode, int resultCode, Intent data) {  
    super.onActivityResult(reqCode, resultCode, data);  
	   
    switch (reqCode) {  
    case (PICK_CONTACT):  
      if (resultCode == Activity.RESULT_OK) {
        if (data.getData() == null) 
          return;

        NameAndNumber nameAndNumber = ContactAccessor.getInstance().getNameAndNumberFromContact(this, data.getData());

        if (nameAndNumber != null)
          recipientsPanel.addRecipient(nameAndNumber.name, nameAndNumber.number);
      }  
      break;  
    }  
  }  
	
  private void initializeDefaults() {
    String name   = getIntent().getStringExtra("name");
    String number = getIntent().getStringExtra("number");
		
    if (number != null)
      recipientsPanel.addRecipient(name, number);
  }
		
  private void initializeResources() {
    recipientsPanel     = (RecipientsPanel)findViewById(R.id.key_recipients);
    addContactButton    = (ImageButton)findViewById(R.id.contacts_button);
    sendButton          = (Button)findViewById(R.id.send_key_button);
    cancelButton        = (Button)findViewById(R.id.cancel_key_button);
    masterSecret        = (MasterSecret)getIntent().getParcelableExtra("master_secret");

    Recipient defaultRecipient = (Recipient)getIntent().getParcelableExtra("recipient");

    if (defaultRecipient != null) {
      recipientsPanel.addRecipient(defaultRecipient.getName(), defaultRecipient.getNumber());
    }
		
    sendButton.setOnClickListener(new SendButtonListener());
    cancelButton.setOnClickListener(new CancelButtonListener());
    addContactButton.setOnClickListener(new AddRecipientButtonListener());
  }
						
  // Listeners
	
  private class AddRecipientButtonListener implements OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(Intent.ACTION_PICK, People.CONTENT_URI);  
      startActivityForResult(intent, PICK_CONTACT);  
    }
  }
	
  private class CancelButtonListener implements OnClickListener {
    public void onClick(View v) {
      SendKeyActivity.this.finish();
    }
  }
	
  private class SendButtonListener implements OnClickListener {
    public void onClick(View v) {
      try {
        Recipients recipients  = recipientsPanel.getRecipients();
				
        if (recipients.isEmpty())
          return;

        KeyExchangeInitiator.initiate(SendKeyActivity.this, masterSecret, recipients.getPrimaryRecipient(), true);
        SendKeyActivity.this.finish();
      } catch (RecipientFormattingException ex) {
        Log.w("compose", ex);
        //alert
      }
    }
  }
}
