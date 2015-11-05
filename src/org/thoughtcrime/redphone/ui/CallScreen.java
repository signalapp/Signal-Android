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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import org.thoughtcrime.redphone.crypto.zrtp.SASInfo;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * A UI widget that encapsulates the entire in-call screen
 * for both initiators and responders.
 *
 * @author Moxie Marlinspike
 *
 */
public class CallScreen extends FrameLayout {

  private CallCard callCard;
  private CallControls callControls;

  public CallScreen(Context context) {
    super(context);
    initialize();
  }

  public CallScreen(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallScreen(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message, @Nullable String sas) {
    callCard.setCard(personInfo, message);
    callControls.setActiveCall(sas);
  }

  public void setActiveCall(Recipient personInfo, String message) {
    callCard.setCard(personInfo, message);
    callControls.setActiveCall();
  }

  public void setIncomingCall(Recipient personInfo) {
    callCard.setCard(personInfo, getContext().getString(R.string.CallScreen_Incoming_call));
    callControls.setIncomingCall();
  }

  public void reset() {
    callCard.reset();
    callControls.reset();
  }

  public void setHangupButtonListener(CallControls.HangupButtonListener listener) {
    callControls.setHangupButtonListener(listener);
  }

  public void setIncomingCallActionListener(CallControls.IncomingCallActionListener listener) {
    callControls.setIncomingCallActionListener(listener);
  }

  public void setMuteButtonListener(CallControls.MuteButtonListener listener) {
    callControls.setMuteButtonListener(listener);
  }

  public void setAudioButtonListener(CallControls.AudioButtonListener listener) {
    callControls.setAudioButtonListener(listener);
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext()
                              .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.redphone_call_screen, this, true);

    this.callCard     = (CallCard)findViewById(R.id.callCard);
    this.callControls = (CallControls)findViewById(R.id.callControls);
  }

  public void notifyBluetoothChange() {
    callControls.updateAudioButton();
  }
}
