package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.multiwaveview.MultiWaveView;
import org.thoughtcrime.securesms.R;

/**
 * Displays the controls at the bottom of the in-call screen.
 *
 * @author Moxie Marlinspike
 *
 */

public class WebRtcIncomingCallOverlay extends RelativeLayout {

  private MultiWaveView incomingCallWidget;
  private TextView      redphoneLabel;

  private Handler handler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      if (incomingCallWidget.getVisibility() == View.VISIBLE) {
        incomingCallWidget.ping();
        handler.sendEmptyMessageDelayed(0, 1200);
      }
    }
  };

  public WebRtcIncomingCallOverlay(Context context) {
    super(context);
    initialize();
  }

  public WebRtcIncomingCallOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcIncomingCallOverlay(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setIncomingCall() {
    Animation animation = incomingCallWidget.getAnimation();

    if (animation != null) {
      animation.reset();
      incomingCallWidget.clearAnimation();
    }

    incomingCallWidget.reset(false);
    incomingCallWidget.setVisibility(View.VISIBLE);
    redphoneLabel.setVisibility(View.VISIBLE);

    handler.sendEmptyMessageDelayed(0, 500);
  }

  public void setActiveCall() {
    incomingCallWidget.setVisibility(View.GONE);
    redphoneLabel.setVisibility(View.GONE);
  }

  public void setActiveCall(@Nullable String sas) {
    setActiveCall();
  }

  public void reset() {
    incomingCallWidget.setVisibility(View.GONE);
    redphoneLabel.setVisibility(View.GONE);
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
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_incoming_call_overlay, this, true);

    this.incomingCallWidget = (MultiWaveView)findViewById(R.id.incomingCallWidget);
    this.redphoneLabel      = (TextView)findViewById(R.id.redphone_banner);
  }

  public static interface IncomingCallActionListener {
    public void onAcceptClick();
    public void onDenyClick();
  }
}
