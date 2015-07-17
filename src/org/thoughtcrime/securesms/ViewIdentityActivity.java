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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.whispersystems.libaxolotl.IdentityKey;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;

/**
 * Activity for displaying an identity key.
 *
 * @author Moxie Marlinspike
 */
public class ViewIdentityActivity extends KeyScanningActivity {

  public static final String IDENTITY_KEY = "identity_key";
  public static final String IDENTITY_TITLE = "identity_title";
  public static final String TITLE        = "title";

  private TextView    identityFingerprint;
  private TextView    identityFingerprintTitle;
  private IdentityKey identityKey;
  private ImageView   imageView;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
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
      imageView.setVisibility(View.GONE);
    } else {
      String fingerprint = identityKey.getFingerprint();
      identityFingerprint.setText(fingerprint);
      BitMatrix matrix = getBitMatrix(fingerprint);
      if(matrix == null) {
        imageView.setVisibility(View.GONE);
        return;
      }
      imageView.setImageBitmap(toBitmap(matrix));
    }

  }


  private void initializeResources() {
    IdentityKeyParcelable identityKeyParcelable = getIntent().getParcelableExtra(IDENTITY_KEY);

    if (identityKeyParcelable == null) {
      throw new AssertionError("No identity key!");
    }

    this.identityKey              = identityKeyParcelable.get();
    this.identityFingerprint      = (TextView)findViewById(R.id.identity_fingerprint);
    this.imageView                = (ImageView)findViewById(R.id.identity_qrcode);
    this.identityFingerprintTitle = (TextView)findViewById(R.id.identity_title);
    String title                  = getIntent().getStringExtra(TITLE);
    String identityTitle          = getIntent().getStringExtra(IDENTITY_TITLE);

    if (title != null) {
      getSupportActionBar().setTitle(getIntent().getStringExtra(TITLE));
    }
    if(identityTitle != null){
      identityFingerprintTitle.setVisibility(View.VISIBLE);
      identityFingerprintTitle.setText(identityTitle);
    }

  }

}
