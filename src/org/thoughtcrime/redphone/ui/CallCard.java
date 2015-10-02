/*
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

package org.thoughtcrime.redphone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * The in-call display card.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallCard extends LinearLayout {

  private ImageView photo;
  private TextView name;
  private TextView phoneNumber;
  private TextView label;
  private TextView elapsedTime;
  private TextView status;

  public CallCard(Context context) {
    super(context);
    initialize();
  }

  public CallCard(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public void reset() {
    setPersonInfo(Recipient.getUnknownRecipient());
    this.status.setText("");
  }

  public void setElapsedTime(String time) {
    this.elapsedTime.setText(time);
  }

  private void setPersonInfo(Recipient recipient) {
    this.photo.setImageDrawable(recipient.getContactPhoto().asCallCard(getContext()));
    this.name.setText(recipient.getName());
    this.phoneNumber.setText(recipient.getNumber());
  }

  public void setCard(Recipient recipient, String status) {
    setPersonInfo(recipient);
    this.status.setText(status);
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext()
                              .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.redphone_call_card, this, true);

    this.elapsedTime = (TextView)findViewById(R.id.elapsedTime);
    this.photo       = (ImageView)findViewById(R.id.photo);
    this.phoneNumber = (TextView)findViewById(R.id.phoneNumber);
    this.name        = (TextView)findViewById(R.id.name);
    this.label       = (TextView)findViewById(R.id.label);
    this.status      = (TextView)findViewById(R.id.callStateLabel);
  }

}
