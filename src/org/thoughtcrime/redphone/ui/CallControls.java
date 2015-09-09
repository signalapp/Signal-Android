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
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.redphone.crypto.zrtp.SASInfo;
import org.thoughtcrime.redphone.util.AudioUtils;
import org.thoughtcrime.redphone.util.multiwaveview.MultiWaveView;
import org.thoughtcrime.securesms.R;

/**
 * Displays the controls at the bottom of the in-call screen.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallControls extends RelativeLayout {

  private ImageButton endCallButton;
  private TextView sasTextView;
//  private ImageButton confirmSasButton;
//  private View confirmSasWrapper;

  private View activeCallWidget;
  private MultiWaveView incomingCallWidget;
  private TextView redphoneLabel;

  private CompoundButton muteButton;
  private InCallAudioButton audioButton;

  private Handler handler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      if (incomingCallWidget.getVisibility() == View.VISIBLE) {
        incomingCallWidget.ping();
        handler.sendEmptyMessageDelayed(0, 1200);
      }
    }
  };

  public CallControls(Context context) {
    super(context);
    initialize();
  }

  public CallControls(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallControls(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setIncomingCall() {
    activeCallWidget.setVisibility(View.GONE);

    Animation animation = incomingCallWidget.getAnimation();

    if (animation != null) {
      animation.reset();
      incomingCallWidget.clearAnimation();
    }

    incomingCallWidget.reset(false);
    incomingCallWidget.setVisibility(View.VISIBLE);
    redphoneLabel.setVisibility(View.VISIBLE);
//    confirmSasWrapper.setVisibility(View.GONE);

    handler.sendEmptyMessageDelayed(0, 500);
  }

  public void setActiveCall() {
    incomingCallWidget.setVisibility(View.GONE);
    redphoneLabel.setVisibility(View.GONE);
    activeCallWidget.setVisibility(View.VISIBLE);
    sasTextView.setVisibility(View.GONE);
//    confirmSasWrapper.setVisibility(View.GONE);
  }

  public void setActiveCall(SASInfo sas) {
    setActiveCall();
    sasTextView.setText(sas.getSasText());
    sasTextView.setVisibility(View.VISIBLE);

//    if (sas.isVerified()) {
//      confirmSasWrapper.setVisibility(View.GONE);
//      sasTextView.setTextColor(Color.parseColor("#ffffff"));
//    } else{
//      confirmSasWrapper.setVisibility(View.VISIBLE);
//      sasTextView.setTextColor(Color.parseColor("#f0a621"));
//    }
  }

  public void reset() {
    incomingCallWidget.setVisibility(View.GONE);
    redphoneLabel.setVisibility(View.GONE);
    activeCallWidget.setVisibility(View.GONE);
    sasTextView.setText("");
    updateAudioButton();
    muteButton.setChecked(false);
  }

  public void setConfirmSasButtonListener(final ConfirmSasButtonListener listener) {
//    confirmSasButton.setOnClickListener(new OnClickListener() {
//      @Override
//      public void onClick(View v) {
//        setActiveCall(new SASInfo(sasTextView.getText().toString(), true));
//        listener.onClick();
//      }
//    });
  }

  public void setHangupButtonListener(final HangupButtonListener listener) {
    endCallButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onClick();
      }
    });
  }

  public void setMuteButtonListener(final MuteButtonListener listener) {
    muteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        listener.onToggle(b);
      }
    });
  }

  public void setAudioButtonListener(final AudioButtonListener listener) {
    audioButton.setListener(listener);
  }


  public void setIncomingCallActionListener(final IncomingCallActionListener listener) {
    incomingCallWidget.setOnTriggerListener(new MultiWaveView.OnTriggerListener() {
      @Override
      public void onTrigger(View v, int target) {
        switch (target) {
          case 0: listener.onAcceptClick(); break;
          case 2: listener.onDenyClick();   break;
        }
      }

      @Override
      public void onReleased(View v, int handle) {}

      @Override
      public void onGrabbedStateChange(View v, int handle) {}

      @Override
      public void onGrabbed(View v, int handle) {}
    });
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext()
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.redphone_call_controls, this, true);

//    this.confirmSasButton   = (ImageButton)findViewById(R.id.confirm_sas);
//    this.confirmSasWrapper  = findViewById(R.id.confirm_wrapper);
    this.endCallButton      = (ImageButton)findViewById(R.id.endButton);
    this.incomingCallWidget = (MultiWaveView)findViewById(R.id.incomingCallWidget);
    this.redphoneLabel      = (TextView)findViewById(R.id.redphone_banner);
    this.activeCallWidget   = (View)findViewById(R.id.inCallControls);
    this.sasTextView        = (TextView)findViewById(R.id.sas);
    this.muteButton         = (CompoundButton)findViewById(R.id.muteButton);
    this.audioButton        = new InCallAudioButton((CompoundButton)findViewById(R.id.audioButton));

    updateAudioButton();
  }

  public void updateAudioButton() {
    audioButton.setAudioMode(AudioUtils.getCurrentAudioMode(getContext()));

//    if(ApplicationPreferencesActivity.getBluetoothEnabled(getContext())) {
//      IntentFilter filter = new IntentFilter();
//      filter.addAction(AudioUtils.getScoUpdateAction());
//      handleBluetoothIntent(getContext().registerReceiver(null, filter));
//    }
  }


  private void handleBluetoothIntent(Intent intent) {
    if (intent == null) {
      return;
    }

    if (!intent.getAction().equals(AudioUtils.getScoUpdateAction())) {
      return;
    }

    Integer state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
//    if (state.equals(AudioManager.SCO_AUDIO_STATE_CONNECTED)
//      && ApplicationPreferencesActivity.getBluetoothEnabled(getContext())) {
//      audioButton.setHeadsetAvailable(true);
//    } else if (state.equals(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)) {
      audioButton.setHeadsetAvailable(false);
//    }
  }


  public static interface ConfirmSasButtonListener {
    public void onClick();
  }

  public static interface HangupButtonListener {
    public void onClick();
  }

  public static interface MuteButtonListener {
    public void onToggle(boolean isMuted);
  }

  public static interface IncomingCallActionListener {
    public void onAcceptClick();
    public void onDenyClick();
  }

  public static interface AudioButtonListener {
    public void onAudioChange(AudioUtils.AudioMode mode);
  }


}
