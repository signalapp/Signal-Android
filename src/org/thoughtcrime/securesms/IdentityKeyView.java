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

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.crypto.MasterSecret;

/**
 * List item view for displaying user identity keys.
 *
 * @author Moxie Marlinspike
 */
public class IdentityKeyView extends RelativeLayout
    implements Recipient.RecipientModifiedListener
{

  private TextView          identityName;
  private TextView          fingerprint;
  private QuickContactBadge contactBadge;
  private ImageView         contactImage;

  private Recipients  recipients;
  private IdentityKey identityKey;

  private final Handler handler = new Handler();

  public IdentityKeyView(Context context) {
    super(context);
  }

  public IdentityKeyView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  public void onFinishInflate() {
    this.identityName = (TextView)findViewById(R.id.identity_name);
    this.fingerprint  = (TextView)findViewById(R.id.fingerprint);
    this.contactBadge = (QuickContactBadge)findViewById(R.id.contact_photo_badge);
    this.contactImage = (ImageView)findViewById(R.id.contact_photo_image);

    if (isBadgeEnabled()) {
      this.contactBadge.setVisibility(View.VISIBLE);
      this.contactImage.setVisibility(View.GONE);
    } else {
      this.contactBadge.setVisibility(View.GONE);
      this.contactImage.setVisibility(View.VISIBLE);
    }
  }

  private boolean isVerified(MasterSecret masterSecret, Context context) {
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);

    return identityDatabase.isIdentityVerified(masterSecret, this.getRecipient(),
            identityKey);
  }

  public void set(IdentityDatabase.Identity identity, MasterSecret masterSecret, Context context) {
    this.recipients  = identity.getRecipients();
    this.identityKey = identity.getIdentityKey();

    this.recipients.addListener(this);

    identityName.setText(recipients.toShortString());
    fingerprint.setText(identity.getIdentityKey().getFingerprint());

    if (this.isVerified(masterSecret, context)) {
      identityName.setTextColor(Color.rgb(0x09, 0xb5, 0x00));
      fingerprint.setTextColor(Color.rgb(0x09, 0xb5, 0x00));
    }

    contactBadge.setImageBitmap(recipients.getPrimaryRecipient().getContactPhoto());
    contactBadge.assignContactFromPhone(recipients.getPrimaryRecipient().getNumber(), true);
    contactImage.setImageBitmap(recipients.getPrimaryRecipient().getContactPhoto());
  }

  public IdentityKey getIdentityKey() {
    return this.identityKey;
  }

  public Recipient getRecipient() {
    return this.recipients.getPrimaryRecipient();
  }

  private boolean isBadgeEnabled() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
  }

  @Override
  public void onModified(Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        IdentityKeyView.this.identityName.setText(recipients.toShortString());
      }
    });
  }
}
