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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SerializableKey;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.MemoryCleaner;

/**
 * Activity for verifying session keys.
 *
 * @author Moxie Marlinspike
 *
 */
public class VerifyKeysActivity extends KeyVerifyingActivity {

  private byte[] yourFingerprintBytes;
  private byte[] theirFingerprintBytes;

  private TextView yourFingerprint;
  private TextView theirFingerprint;

  private Recipient recipient;
  private MasterSecret masterSecret;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.verify_keys_activity);

    initializeResources();
    initializeFingerprints();
  }

  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override
  protected void handleVerified() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setTitle("Mark Session Verified?");
    builder.setMessage("Are you sure that you have validated these fingerprints and " +
                       "would like to mark this session as verified?");
    builder.setPositiveButton("Mark Verified", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        SessionRecord sessionRecord = new SessionRecord(VerifyKeysActivity.this, masterSecret,
                                                        recipient);
        sessionRecord.setVerifiedSessionKey(true);
        sessionRecord.save();
        VerifyKeysActivity.this.finish();
      }
    });

    builder.setNegativeButton("Cancel", null);
    builder.show();
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
    return "Get my fingerprint scanned";
  }

  @Override
  protected String getScanString() {
    return "Scan their fingerprint";
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
    return "WARNING, the scanned key DOES NOT match! Please check the fingerprint text carefully.";
  }

  @Override
  protected String getNotVerifiedTitle() {
    return "NOT Verified!";
  }

  @Override
  protected String getVerifiedMessage() {
    return "Their key is correct. It is also necessary to get your fingerprint scanned as well.";
  }

  @Override
  protected String getVerifiedTitle() {
    return "Verified!";
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
