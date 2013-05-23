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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * List item view for displaying user identity keys.
 *
 * @author Moxie Marlinspike
 */
public class IdentityKeyView extends RelativeLayout
    implements Recipient.RecipientModifiedListener
{

  private TextView identityName;

  private Recipients  recipients;
  private IdentityKey identityKey;

  private final Handler handler = new Handler();

  public IdentityKeyView(Context context) {
    super(context);

    LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    li.inflate(R.layout.identity_key_view, this, true);

    initializeResources();
  }

  public IdentityKeyView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  public void set(IdentityDatabase.Identity identity) {
    this.recipients  = identity.getRecipients();
    this.identityKey = identity.getIdentityKey();

    this.recipients.addListener(this);

    identityName.setText(recipients.toShortString());
  }

  public IdentityKey getIdentityKey() {
    return this.identityKey;
  }

  private void initializeResources() {
    this.identityName = (TextView)findViewById(R.id.identity_name);
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
