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

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity for displaying an identity key.
 * 
 * @author Moxie Marlinspike
 */
public class ViewIdentityActivity extends KeyScanningActivity {

  private TextView identityFingerprint;
  private Button compareButton;
  private Button okButton;
  private IdentityKey identityKey;
	
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.view_identity_activity);
		
    initializeResources();
    initializeListeners();
    initializeFingerprint();
  }
	
  private void initializeFingerprint() {
    if (identityKey == null) {
      identityFingerprint.setText("You do not have an identity key.");
    } else {
      identityFingerprint.setText(identityKey.getFingerprint());
    }
  }
	
  private void initializeListeners() {
    this.okButton.setOnClickListener(new OkButtonListener());
    this.compareButton.setOnClickListener(new CompareListener());
  }
	
  private void initializeResources() {
    this.identityKey         = (IdentityKey)getIntent().getParcelableExtra("identity_key");
    this.identityFingerprint = (TextView)findViewById(R.id.identity_fingerprint);
    this.okButton	         = (Button)findViewById(R.id.ok_button);
    this.compareButton       = (Button)findViewById(R.id.compare_button);
  }
	
  private class CompareListener implements View.OnClickListener {
    public void onClick(View v) {
      registerForContextMenu(compareButton);
      compareButton.showContextMenu();
    }
  }
	
  private class OkButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }

  @Override
  protected String getScanString() {
    return "Scan to compare";
  }

  @Override
  protected String getDisplayString() {
    return "Get scanned to compare";
  }

  @Override
  protected IdentityKey getIdentityKeyToCompare() {
    return identityKey;
  }

  @Override
  protected IdentityKey getIdentityKeyToDisplay() {
    return identityKey;
  }

  @Override
  protected String getNotVerifiedMessage() {
    return  "WARNING, the scanned key DOES NOT match!";
  }

  @Override
  protected String getNotVerifiedTitle() {
    return "NOT Verified!";
  }

  @Override
  protected String getVerifiedMessage() {
    return "The scanned key matches!";
  }

  @Override
  protected String getVerifiedTitle() {
    return "Verified!";
  }
}
