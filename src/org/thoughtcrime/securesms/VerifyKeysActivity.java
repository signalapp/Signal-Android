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
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SerializableKey;
import org.thoughtcrime.securesms.database.keys.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.MemoryCleaner;

/**
 * Activity for verifying session keys.
 *
 * @author Moxie Marlinspike
 *
 */
public class VerifyKeysActivity extends KeyScanningActivity {

  private byte[] yourFingerprintBytes;
  private byte[] theirFingerprintBytes;

  private TextView yourFingerprint;
  private TextView theirFingerprint;

  private Recipient recipient;
  private MasterSecret masterSecret;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.verify_keys_activity);

    initializeResources();
    initializeFingerprints();
  }

  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  private void initializeResources() {
    this.recipient        = (Recipient)this.getIntent().getParcelableExtra("recipient");
    this.masterSecret     = (MasterSecret)this.getIntent().getParcelableExtra("master_secret");
    this.yourFingerprint  = (TextView)findViewById(R.id.you_read);
    this.theirFingerprint = (TextView)findViewById(R.id.friend_reads);
  }

  private void initializeFingerprints() {
    SessionRecord session      = new SessionRecord(this, masterSecret, recipient);
    this.yourFingerprintBytes  = session.getLocalFingerprint();
    this.theirFingerprintBytes = session.getRemoteFingerprint();

    this.yourFingerprint.setText(Hex.toString(yourFingerprintBytes));
    this.theirFingerprint.setText(Hex.toString(theirFingerprintBytes));
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.VerifyKeysActivity_get_my_fingerprint_scanned);
  }

  @Override
  protected String getScanString() {
    return getString(R.string.VerifyKeysActivity_scan_their_fingerprint);
  }

  @Override
  protected SerializableKey getIdentityKeyToCompare() {
    return new FingerprintKey(theirFingerprintBytes);
  }

  @Override
  protected SerializableKey getIdentityKeyToDisplay() {
    return new FingerprintKey(yourFingerprintBytes);
  }

  @Override
  protected String getNotVerifiedMessage() {
    return getString(R.string.VerifyKeysActivity_warning_the_scanned_key_does_not_match_please_check_the_fingerprint_text_carefully2);
  }

  @Override
  protected String getNotVerifiedTitle() {
    return getString(R.string.VerifyKeysActivity_not_verified_exclamation);
  }

  @Override
  protected String getVerifiedMessage() {
    return getString(R.string.VerifyKeysActivity_their_key_is_correct_it_is_also_necessary_to_get_your_fingerprint_scanned_as_well);
  }

  @Override
  protected String getVerifiedTitle() {
    return getString(R.string.VerifyKeysActivity_verified_exclamation);
  }

  private class FingerprintKey implements SerializableKey {
    private final byte[] fingerprint;

    public FingerprintKey(byte[] fingerprint) {
      this.fingerprint = fingerprint;
    }

    public byte[] serialize() {
      return fingerprint;
    }
  }

}
