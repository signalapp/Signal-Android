package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.PorterDuff;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.mms.GlideRequests;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.ThemeUtil;


import java.util.List;

import network.loki.messenger.R;

public class ConversationTypingView extends LinearLayout {

  private AvatarImageView     avatar;
  private View                bubble;
  private TypingIndicatorView indicator;

  public ConversationTypingView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    avatar    = findViewById(R.id.typing_avatar);
    bubble    = findViewById(R.id.typing_bubble);
    indicator = findViewById(R.id.typing_indicator);
  }

  public void setTypists(@NonNull GlideRequests glideRequests, @NonNull List<Recipient> typists, boolean isGroupThread) {
    if (typists.isEmpty()) {
      indicator.stopAnimation();
      return;
    }

    Recipient typist = typists.get(0);

    bubble.getBackground().setColorFilter(
            ThemeUtil.getThemedColor(getContext(), R.attr.message_received_background_color),
            PorterDuff.Mode.MULTIPLY);

    if (isGroupThread) {
      avatar.setAvatar(glideRequests, typist, false);
      avatar.setVisibility(VISIBLE);
    } else {
      avatar.setVisibility(GONE);
    }

    indicator.startAnimation();
  }
}
