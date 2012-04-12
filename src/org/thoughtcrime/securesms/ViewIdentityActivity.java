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
import org.thoughtcrime.securesms.lang.BhoButton;
import org.thoughtcrime.securesms.lang.BhoTextView;

import android.os.Bundle;
import android.view.View;

/**
 * Activity for displaying an identity key.
 * 
 * @author Moxie Marlinspike
 */
public class ViewIdentityActivity extends KeyScanningActivity {

  private BhoTextView identityFingerprint;
  private BhoButton compareButton;
  private BhoButton okButton;
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
      identityFingerprint.setText(R.string.you_do_not_have_an_identity_key_);
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
    this.identityFingerprint = (BhoTextView)findViewById(R.id.identity_fingerprint);
    this.okButton	         = (BhoButton)findViewById(R.id.ok_button);
    this.compareButton       = (BhoButton)findViewById(R.id.compare_button);
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
    return getString(R.string.scan_to_compare);
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.get_scanned_to_compare);
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
    return  getString(R.string.warning_the_scanned_key_does_not_match_);
  }

  @Override
  protected String getNotVerifiedTitle() {
    return getString(R.string.not_verified_);
  }

  @Override
  protected String getVerifiedMessage() {
    return getString(R.string.the_scanned_key_matches_);
  }

  @Override
  protected String getVerifiedTitle() {
    return getString(R.string.verified_);
  }
}
