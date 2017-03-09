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
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
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
public class WebRtcCallScreen extends FrameLayout implements Recipient.RecipientModifiedListener {

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

  private Recipient recipient;
  private boolean   minimized;

  private WebRtcIncomingCallOverlay incomingCallOverlay;

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
    incomingCallOverlay.setActiveCall(sas);
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message) {
    setCard(personInfo, message);
    incomingCallOverlay.setActiveCall();
  }

  public void setIncomingCall(Recipient personInfo) {
    setCard(personInfo, getContext().getString(R.string.CallScreen_Incoming_call));
    incomingCallOverlay.setIncomingCall();
    endCallButton.setVisibility(View.INVISIBLE);
  }

  public void setUntrustedIdentity(Recipient personInfo, IdentityKey untrustedIdentity) {
    String          name            = recipient.toShortString();
    String          introduction    = String.format(getContext().getString(R.string.WebRtcCallScreen_new_safety_numbers), name, name);
    SpannableString spannableString = new SpannableString(introduction + " " + getContext().getString(R.string.WebRtcCallScreen_you_may_wish_to_verify_this_contact));

    spannableString.setSpan(new VerifySpan(getContext(), personInfo.getRecipientId(), untrustedIdentity),
                            introduction.length()+1, spannableString.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    setPersonInfo(personInfo);

    this.incomingCallOverlay.setActiveCall();
    this.status.setText(R.string.WebRtcCallScreen_new_safety_numbers_title);
    this.untrustedIdentityContainer.setVisibility(View.VISIBLE);
    this.untrustedIdentityExplanation.setText(spannableString);
    this.untrustedIdentityExplanation.setMovementMethod(LinkMovementMethod.getInstance());

    this.endCallButton.setVisibility(View.INVISIBLE);
  }


  public void reset() {
    setPersonInfo(Recipient.getUnknownRecipient());
    setMinimized(false);
    this.status.setText("");
    this.recipient = null;

    this.controls.reset();
    this.untrustedIdentityExplanation.setText("");
    this.untrustedIdentityContainer.setVisibility(View.GONE);
    this.localRenderLayout.removeAllViews();
    this.remoteRenderLayout.removeAllViews();

    incomingCallOverlay.reset();
  }

  public void setIncomingCallActionListener(WebRtcIncomingCallOverlay.IncomingCallActionListener listener) {
    incomingCallOverlay.setIncomingCallActionListener(listener);
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
    endCallButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onClick();
      }
    });
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

    this.elapsedTime                  = (TextView) findViewById(R.id.elapsedTime);
    this.photo                        = (ImageView) findViewById(R.id.photo);
    this.localRenderLayout            = (PercentFrameLayout) findViewById(R.id.local_render_layout);
    this.remoteRenderLayout           = (PercentFrameLayout) findViewById(R.id.remote_render_layout);
    this.phoneNumber                  = (TextView) findViewById(R.id.phoneNumber);
    this.name                         = (TextView) findViewById(R.id.name);
    this.label                        = (TextView) findViewById(R.id.label);
    this.status                       = (TextView) findViewById(R.id.callStateLabel);
    this.controls                     = (WebRtcCallControls) findViewById(R.id.inCallControls);
    this.endCallButton                = (FloatingActionButton) findViewById(R.id.hangup_fab);
    this.incomingCallOverlay          = (WebRtcIncomingCallOverlay) findViewById(R.id.callControls);
    this.untrustedIdentityContainer   = findViewById(R.id.untrusted_layout);
    this.untrustedIdentityExplanation = (TextView) findViewById(R.id.untrusted_explanation);
    this.acceptIdentityButton         = (Button)findViewById(R.id.accept_safety_numbers);
    this.cancelIdentityButton         = (Button)findViewById(R.id.cancel_safety_numbers);
    this.expandedInfo                 = (RelativeLayout)findViewById(R.id.expanded_info);
    this.callHeader                   = (ViewGroup)findViewById(R.id.call_info_1);

    this.localRenderLayout.setHidden(true);
    this.remoteRenderLayout.setHidden(true);
    this.minimized = false;

    this.remoteRenderLayout.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        setMinimized(!minimized);
      }
    });
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

    final Context context = getContext();

    new AsyncTask<Void, Void, ContactPhoto>() {
      @Override
      protected ContactPhoto doInBackground(Void... params) {
        DisplayMetrics metrics       = new DisplayMetrics();
        WindowManager  windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Uri            contentUri    = ContactsContract.Contacts.lookupContact(context.getContentResolver(),
                                                                               recipient.getContactUri());
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return ContactPhotoFactory.getContactPhoto(context, contentUri, null, metrics.widthPixels);
      }

      @Override
      protected void onPostExecute(final ContactPhoto contactPhoto) {
        WebRtcCallScreen.this.photo.setImageDrawable(contactPhoto.asCallCard(context));
      }
    }.execute();

    this.name.setText(recipient.getName());
    this.phoneNumber.setText(recipient.getNumber());
  }

  private void setCard(Recipient recipient, String status) {
    setPersonInfo(recipient);
    this.status.setText(status);
    this.untrustedIdentityContainer.setVisibility(View.GONE);
    this.endCallButton.setVisibility(View.VISIBLE);
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
      ViewCompat.animate(endCallButton).alpha(1).withEndAction(new Runnable() {
        @Override
        public void run() {
          // Note: This is to work around an Android bug, see #6225
          endCallButton.requestLayout();
        }
      });

      this.minimized = false;
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    if (recipient == this.recipient) {
      setPersonInfo(recipient);
    }
  }

  public static interface HangupButtonListener {
    public void onClick();
  }


}
