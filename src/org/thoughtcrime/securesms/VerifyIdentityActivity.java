/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;

import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

/**
 * Activity for verifying identity keys.
 *
 * @author Moxie Marlinspike
 */
public class VerifyIdentityActivity extends KeyScanningActivity {

  private Recipient    recipient;
  private MasterSecret masterSecret;

  private TextView     localIdentityFingerprint;
  private TextView     remoteIdentityFingerprint;
  private Button       scanButton;
  private ImageView    qrCode;
  private ZXingScannerView scannerView;

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();


  @Override
  public void onCreate(Bundle state) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(state);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.verify_identity_activity);

    initializeResources();
    initializeFingerprints();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.AndroidManifest__verify_identity);
  }

  private void hideScanner() {
    scanButton.setVisibility(View.VISIBLE);
    scannerView.setVisibility(View.GONE);
    scannerView.stopCamera();

  }

  private void showScanner() {
    scanButton.setVisibility(View.GONE);
    scannerView.setVisibility(View.VISIBLE);
    scannerView.setResultHandler(new ZXingScannerView.ResultHandler() {
      @Override
      public void handleResult(Result result) {
        checkKey(result.getText());
        hideScanner();
      }
    });
    scannerView.setAutoFocus(true);
    scannerView.startCamera();
  }


  @Override
  protected void onPause() {
    super.onPause();

    scannerView.stopCamera();
    scannerView.setResultHandler(null);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home:     finish();          return true;
    }

    return false;
  }


  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  private void initializeLocalIdentityKey() {
    if (!IdentityKeyUtil.hasIdentityKey(this)) {
      localIdentityFingerprint.setText(R.string.VerifyIdentityActivity_you_do_not_have_an_identity_key);
      return;
    }

    IdentityKey identityKey = IdentityKeyUtil.getIdentityKey(this);
    localIdentityFingerprint.setText(identityKey.getFingerprint());

    String fingerprint = identityKey.getFingerprint();
    BitMatrix matrix = getBitMatrix(fingerprint);
    if(matrix == null) {
      qrCode.setVisibility(View.GONE);
      return;
    }
    qrCode.setImageBitmap(toBitmap(matrix));
  }

  private void initializeRemoteIdentityKey() {
    IdentityKeyParcelable identityKeyParcelable = getIntent().getParcelableExtra("remote_identity");
    IdentityKey           identityKey           = null;

    if (identityKeyParcelable != null) {
      identityKey = identityKeyParcelable.get();
    }

    if (identityKey == null) {
      identityKey = getRemoteIdentityKey(masterSecret, recipient);
    }

    if (identityKey == null) {
      remoteIdentityFingerprint.setText(R.string.VerifyIdentityActivity_recipient_has_no_identity_key);
    } else {
      remoteIdentityFingerprint.setText(identityKey.getFingerprint());
    }
  }

  private void initializeFingerprints() {
    initializeLocalIdentityKey();
    initializeRemoteIdentityKey();
  }

  private void initializeResources() {
    this.localIdentityFingerprint  = (TextView)findViewById(R.id.you_read);
    this.remoteIdentityFingerprint = (TextView)findViewById(R.id.friend_reads);
    this.scanButton                = (Button)findViewById(R.id.scan_qr_code);
    this.qrCode = (ImageView) findViewById(R.id.identity_qrcode);
    this.recipient                 = RecipientFactory.getRecipientForId(this, this.getIntent().getLongExtra("recipient", -1), true);
    this.masterSecret              = this.getIntent().getParcelableExtra("master_secret");

    this.scannerView               = (ZXingScannerView)findViewById(R.id.camera_preview);
    this.scanButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showScanner();
      }
    });
  }

  private void checkKey(String key) {
    IdentityKey identityKey = getRemoteIdentityKey(masterSecret, recipient);
    if(identityKey != null) {
      String fingerprint = identityKey.getFingerprint();
      if (fingerprint.equalsIgnoreCase(key)) {
        remoteIdentityFingerprint.setText(R.string.identification_verified_successfully);
        remoteIdentityFingerprint.setTextColor(Color.GREEN);
      } else {
        remoteIdentityFingerprint.setText(R.string.identification_verified_unsuccessfully);
        remoteIdentityFingerprint.setTextColor(Color.RED);
      }
    }
  }


  private IdentityKey getRemoteIdentityKey(MasterSecret masterSecret, Recipient recipient) {
    SessionStore  sessionStore = new TextSecureSessionStore(this, masterSecret);
    AxolotlAddress axolotlAddress = new AxolotlAddress(recipient.getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
    SessionRecord  record         = sessionStore.loadSession(axolotlAddress);

    if (record == null) {
      return null;
    }

    return record.getSessionState().getRemoteIdentityKey();
  }
}
