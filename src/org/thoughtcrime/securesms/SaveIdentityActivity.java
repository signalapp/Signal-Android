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
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Activity that provides interface for users to save
 * identity keys they receive.
 * 
 * @author Moxie Marlinspike
 */
public class SaveIdentityActivity extends Activity {

  private MasterSecret masterSecret;
  private IdentityKey identityKey;
	
  private EditText identityName;
  private Button okButton;
  private Button cancelButton;
	
  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.save_identity_activity);
		
    initializeResources();
    initializeListeners();
  }
	
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }
  
  private void initializeResources() {
    String nameSuggestion = getIntent().getStringExtra("name_suggestion");

    this.masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");
    this.identityKey  = (IdentityKey)getIntent().getParcelableExtra("identity_key");
    this.identityName = (EditText)findViewById(R.id.identity_name);
    this.okButton     = (Button)findViewById(R.id.ok_button);
    this.cancelButton = (Button)findViewById(R.id.cancel_button);
		
    if ((nameSuggestion != null) && (nameSuggestion.trim().length() > 0)) {
      this.identityName.setText(nameSuggestion);
    }
  }
	
  private void initializeListeners() {
    this.okButton.setOnClickListener(new OkListener());
    this.cancelButton.setOnClickListener(new CancelListener());
  }
	
  private class OkListener implements View.OnClickListener {
    public void onClick(View v) {
      if (identityName.getText() == null || identityName.getText().toString().trim().length() == 0) {
        Toast.makeText(SaveIdentityActivity.this, "You must specify a name for this identity!", Toast.LENGTH_LONG).show();
        return;
      }
			
      try {
        DatabaseFactory.getIdentityDatabase(SaveIdentityActivity.this).saveIdentity(masterSecret, identityKey, identityName.getText().toString());
      } catch (InvalidKeyException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(SaveIdentityActivity.this);
        builder.setTitle("Identity Name Exists!");
        builder.setMessage("An identity key with the specified name already exists.");
        builder.setPositiveButton("Manage Identities", new DialogInterface.OnClickListener() {					
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(SaveIdentityActivity.this, ReviewIdentitiesActivity.class);
            intent.putExtra("master_secret", masterSecret);
            startActivity(intent);
          }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
        return;
      }
			
      finish();
    }		
  }
	
  private class CancelListener implements View.OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }
	
}
