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

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.IdentityKey;

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
      identityFingerprint.setText(R.string.ViewIdentityActivity_you_do_not_have_an_identity_key);
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
    this.okButton	           = (Button)findViewById(R.id.ok_button);
    this.compareButton       = (Button)findViewById(R.id.compare_button);
  }

  private class CompareListener implements View.OnClickListener {
    public void onClick(View v) {
      initiateDisplay();
    }
  }

  private class OkButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }

  @Override
  protected String getScanString() {
    return getString(R.string.ViewIdentityActivity_scan_to_compare);
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.ViewIdentityActivity_get_scanned_to_compare);
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
    return  getString(R.string.ViewIdentityActivity_warning_the_scanned_key_does_not_match_exclamation);
  }

  @Override
  protected String getNotVerifiedTitle() {
    return getString(R.string.ViewIdentityActivity_not_verified_exclamation);
  }

  @Override
  protected String getVerifiedMessage() {
    return getString(R.string.ViewIdentityActivity_the_scanned_key_matches_exclamation);
  }

  @Override
  protected String getVerifiedTitle() {
    return getString(R.string.ViewIdentityActivity_verified_exclamation);
  }
}
