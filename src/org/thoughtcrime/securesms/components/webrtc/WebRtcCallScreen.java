/*
 * Copyright (C) 2016 Open Whisper Systems
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

package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VerifySpan;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.SurfaceViewRenderer;
import org.whispersystems.libsignal.IdentityKey;

/**
 * A UI widget that encapsulates the entire in-call screen
 * for both initiators and responders.
 *
 * @author Moxie Marlinspike
 *
 */
public class WebRtcCallScreen extends FrameLayout implements RecipientModifiedListener {

  @SuppressWarnings("unused")
  private static final String TAG = WebRtcCallScreen.class.getSimpleName();

  private ImageView            photo;
  private PercentFrameLayout   localRenderLayout;
  private PercentFrameLayout   remoteRenderLayout;
  private TextView             name;
  private TextView             phoneNumber;
  private TextView             label;
  private TextView             elapsedTime;
  private View                 untrustedIdentityContainer;
  private TextView             untrustedIdentityExplanation;
  private Button               acceptIdentityButton;
  private Button               cancelIdentityButton;
  private TextView             status;
  private FloatingActionButton endCallButton;
  private WebRtcCallControls   controls;
  private RelativeLayout       expandedInfo;
  private ViewGroup            callHeader;

  private WebRtcAnswerDeclineButton incomingCallButton;

  private Recipient recipient;
  private boolean   minimized;


  public WebRtcCallScreen(Context context) {
    super(context);
    initialize();
  }

  public WebRtcCallScreen(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcCallScreen(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message, @Nullable String sas) {
    setCard(personInfo, message);
    setConnected(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer);
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message) {
    setCard(personInfo, message);
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setIncomingCall(Recipient personInfo) {
    setCard(personInfo, getContext().getString(R.string.CallScreen_Incoming_call));
    endCallButton.setVisibility(View.INVISIBLE);
    incomingCallButton.setVisibility(View.VISIBLE);
    incomingCallButton.startRingingAnimation();
  }

  public void setUntrustedIdentity(Recipient personInfo, IdentityKey untrustedIdentity) {
    String          name            = recipient.toShortString();
    String          introduction    = String.format(getContext().getString(R.string.WebRtcCallScreen_new_safety_numbers), name, name);
    SpannableString spannableString = new SpannableString(introduction + " " + getContext().getString(R.string.WebRtcCallScreen_you_may_wish_to_verify_this_contact));

    spannableString.setSpan(new VerifySpan(getContext(), personInfo.getAddress(), untrustedIdentity),
                            introduction.length()+1, spannableString.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    setPersonInfo(personInfo);

    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    this.status.setText(R.string.WebRtcCallScreen_new_safety_number_title);
    this.untrustedIdentityContainer.setVisibility(View.VISIBLE);
    this.untrustedIdentityExplanation.setText(spannableString);
    this.untrustedIdentityExplanation.setMovementMethod(LinkMovementMethod.getInstance());

    this.endCallButton.setVisibility(View.INVISIBLE);
  }

  public void setIncomingCallActionListener(WebRtcAnswerDeclineButton.AnswerDeclineListener listener) {
    incomingCallButton.setAnswerDeclineListener(listener);
  }

  public void setAudioMuteButtonListener(WebRtcCallControls.MuteButtonListener listener) {
    this.controls.setAudioMuteButtonListener(listener);
  }

  public void setVideoMuteButtonListener(WebRtcCallControls.MuteButtonListener listener) {
    this.controls.setVideoMuteButtonListener(listener);
  }

  public void setSpeakerButtonListener(WebRtcCallControls.SpeakerButtonListener listener) {
    this.controls.setSpeakerButtonListener(listener);
  }

  public void setBluetoothButtonListener(WebRtcCallControls.BluetoothButtonListener listener) {
    this.controls.setBluetoothButtonListener(listener);
  }

  public void setHangupButtonListener(final HangupButtonListener listener) {
    endCallButton.setOnClickListener(v -> listener.onClick());
  }

  public void setAcceptIdentityListener(OnClickListener listener) {
    this.acceptIdentityButton.setOnClickListener(listener);
  }

  public void setCancelIdentityButton(OnClickListener listener) {
    this.cancelIdentityButton.setOnClickListener(listener);
  }

  public void updateAudioState(boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {
    this.controls.updateAudioState(isBluetoothAvailable);
    this.controls.setMicrophoneEnabled(isMicrophoneEnabled);
  }

  public void setControlsEnabled(boolean enabled) {
    this.controls.setControlsEnabled(enabled);
  }

  public void setLocalVideoEnabled(boolean enabled) {
    if (enabled && this.localRenderLayout.isHidden()) {
      this.controls.setVideoEnabled(true);
      this.localRenderLayout.setHidden(false);
      this.localRenderLayout.requestLayout();
    } else  if (!enabled && !this.localRenderLayout.isHidden()){
      this.controls.setVideoEnabled(false);
      this.localRenderLayout.setHidden(true);
      this.localRenderLayout.requestLayout();
    }
  }

  public void setRemoteVideoEnabled(boolean enabled) {
    if (enabled && this.remoteRenderLayout.isHidden()) {
      this.photo.setVisibility(View.INVISIBLE);
      setMinimized(true);

      this.remoteRenderLayout.setHidden(false);
      this.remoteRenderLayout.requestLayout();

      if (localRenderLayout.isHidden()) this.controls.displayVideoTooltip(callHeader);
    } else if (!enabled && !this.remoteRenderLayout.isHidden()){
      setMinimized(false);
      this.photo.setVisibility(View.VISIBLE);
      this.remoteRenderLayout.setHidden(true);
      this.remoteRenderLayout.requestLayout();
    }
  }

  public boolean isVideoEnabled() {
    return controls.isVideoEnabled();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_screen, this, true);

    this.elapsedTime                  = findViewById(R.id.elapsedTime);
    this.photo                        = findViewById(R.id.photo);
    this.localRenderLayout            = findViewById(R.id.local_render_layout);
    this.remoteRenderLayout           = findViewById(R.id.remote_render_layout);
    this.phoneNumber                  = findViewById(R.id.phoneNumber);
    this.name                         = findViewById(R.id.name);
    this.label                        = findViewById(R.id.label);
    this.status                       = findViewById(R.id.callStateLabel);
    this.controls                     = findViewById(R.id.inCallControls);
    this.endCallButton                = findViewById(R.id.hangup_fab);
    this.incomingCallButton           = findViewById(R.id.answer_decline_button);
    this.untrustedIdentityContainer   = findViewById(R.id.untrusted_layout);
    this.untrustedIdentityExplanation = findViewById(R.id.untrusted_explanation);
    this.acceptIdentityButton         = findViewById(R.id.accept_safety_numbers);
    this.cancelIdentityButton         = findViewById(R.id.cancel_safety_numbers);
    this.expandedInfo                 = findViewById(R.id.expanded_info);
    this.callHeader                   = findViewById(R.id.call_info_1);

    this.localRenderLayout.setHidden(true);
    this.remoteRenderLayout.setHidden(true);
    this.minimized = false;

    this.remoteRenderLayout.setOnClickListener(v -> setMinimized(!minimized));
  }

  private void setConnected(SurfaceViewRenderer localRenderer,
                            SurfaceViewRenderer remoteRenderer)
  {
    if (localRenderLayout.getChildCount() == 0 && remoteRenderLayout.getChildCount() == 0) {
      if (localRenderer.getParent() != null) {
        ((ViewGroup)localRenderer.getParent()).removeView(localRenderer);
      }

      if (remoteRenderer.getParent() != null) {
        ((ViewGroup)remoteRenderer.getParent()).removeView(remoteRenderer);
      }

      localRenderLayout.setPosition(7, 70, 25, 25);
      localRenderLayout.setSquare(true);
      remoteRenderLayout.setPosition(0, 0, 100, 100);

      localRenderer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                 ViewGroup.LayoutParams.MATCH_PARENT));

      remoteRenderer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                  ViewGroup.LayoutParams.MATCH_PARENT));

      localRenderer.setMirror(true);
      localRenderer.setZOrderMediaOverlay(true);

      localRenderLayout.addView(localRenderer);
      remoteRenderLayout.addView(remoteRenderer);
    }
  }

  private void setPersonInfo(final @NonNull Recipient recipient) {
    this.recipient = recipient;
    this.recipient.addListener(this);

    GlideApp.with(getContext().getApplicationContext())
            .load(recipient.getContactPhoto())
            .fallback(recipient.getFallbackContactPhoto().asCallCard(getContext()))
            .error(recipient.getFallbackContactPhoto().asCallCard(getContext()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(this.photo);

    this.name.setText(recipient.getName());

    if (recipient.getName() == null && !TextUtils.isEmpty(recipient.getProfileName())) {
      this.phoneNumber.setText(recipient.getAddress().serialize() + " (~" + recipient.getProfileName() + ")");
    } else {
      this.phoneNumber.setText(recipient.getAddress().serialize());
    }
  }

  private void setCard(Recipient recipient, String status) {
    setPersonInfo(recipient);
    this.status.setText(status);
    this.untrustedIdentityContainer.setVisibility(View.GONE);
  }

  private void setMinimized(boolean minimized) {
    if (minimized) {
      ViewCompat.animate(callHeader).translationY(-1 * expandedInfo.getHeight());
      ViewCompat.animate(status).alpha(0);
      ViewCompat.animate(endCallButton).translationY(endCallButton.getHeight() + ViewUtil.dpToPx(getContext(), 40));
      ViewCompat.animate(endCallButton).alpha(0);

      this.minimized = true;
    } else {
      ViewCompat.animate(callHeader).translationY(0);
      ViewCompat.animate(status).alpha(1);
      ViewCompat.animate(endCallButton).translationY(0);
      ViewCompat.animate(endCallButton).alpha(1).withEndAction(() -> {
        // Note: This is to work around an Android bug, see #6225
        endCallButton.requestLayout();
      });

      this.minimized = false;
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> {
      if (recipient == WebRtcCallScreen.this.recipient) {
        setPersonInfo(recipient);
      }
    });
  }

  public interface HangupButtonListener {
    void onClick();
  }


}
