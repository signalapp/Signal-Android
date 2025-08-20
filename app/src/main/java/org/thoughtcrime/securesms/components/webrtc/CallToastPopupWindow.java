package org.thoughtcrime.securesms.components.webrtc;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.concurrent.TimeUnit;

/**
 * Top screen toast to be shown to the user for 3 seconds.
 *
 * Currently hard coded to show specific text, but could be easily expanded to be customizable
 * if desired. Based on {@link CallParticipantsListUpdatePopupWindow}.
 */
public class CallToastPopupWindow extends PopupWindow {

  private static final long DURATION = TimeUnit.SECONDS.toMillis(3);

  private final ViewGroup parent;

  public static void show(@NonNull ViewGroup viewGroup) {
    CallToastPopupWindow toast = new CallToastPopupWindow(viewGroup);
    toast.show();
  }

  public static void show(@NonNull ViewGroup viewGroup, @DrawableRes int iconId, @NonNull String description) {
    CallToastPopupWindow toast = new CallToastPopupWindow(viewGroup);

    TextView text = toast.getContentView().findViewById(R.id.description);
    text.setText(description);
    text.setCompoundDrawablesRelativeWithIntrinsicBounds(iconId, 0, 0, 0);
    toast.show();
  }

  private CallToastPopupWindow(@NonNull ViewGroup parent) {
    super(LayoutInflater.from(parent.getContext()).inflate(R.layout.call_toast_popup_window, parent, false),
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewUtil.dpToPx(94));

    this.parent = parent;

    setAnimationStyle(R.style.PopupAnimation);
  }

  public void show() {
    showAtLocation(parent, Gravity.TOP | Gravity.START, 0, 0);
    measureChild();
    update();
    getContentView().postDelayed(this::dismiss, DURATION);
  }

  private void measureChild() {
    getContentView().measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                             View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
  }
}
