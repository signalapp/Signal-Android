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
import android.support.annotation.NonNull;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.whispersystems.libsignal.IdentityKey;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;

/**
 * Activity for displaying an identity key.
 *
 * @author Moxie Marlinspike
 */
public class ViewIdentityActivity extends KeyScanningActivity {

  public static final String IDENTITY_KEY = "identity_key";
  public static final String TITLE        = "title";

  private TextView    identityFingerprint;
  private IdentityKey identityKey;

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.view_identity_activity);

    initialize();
  }

  protected void initialize() {
    initializeResources();
    initializeFingerprint();
  }

  private void initializeFingerprint() {
    if (identityKey == null) {
      identityFingerprint.setText(R.string.ViewIdentityActivity_you_do_not_have_an_identity_key);
    } else {
      identityFingerprint.setText(identityKey.getFingerprint());
    }
  }

  private void initializeResources() {
    IdentityKeyParcelable identityKeyParcelable = getIntent().getParcelableExtra(IDENTITY_KEY);

    if (identityKeyParcelable == null) {
      throw new AssertionError("No identity key!");
    }

    this.identityKey         = identityKeyParcelable.get();
    this.identityFingerprint = (TextView)findViewById(R.id.identity_fingerprint);
    String title             = getIntent().getStringExtra(TITLE);

    if (title != null) {
      getSupportActionBar().setTitle(getIntent().getStringExtra(TITLE));
    }
  }

  @Override
  protected String getScanString() {
    return getString(R.string.ViewIdentityActivity_scan_contacts_qr_code);
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.ViewIdentityActivity_display_your_qr_code);
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
