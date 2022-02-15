package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.Pair;

import java.util.LinkedList;
import java.util.List;

public class ConversationTypingView extends ConstraintLayout {

  private AvatarImageView     avatar1;
  private AvatarImageView     avatar2;
  private AvatarImageView     avatar3;
  private BadgeImageView      badge1;
  private BadgeImageView      badge2;
  private BadgeImageView      badge3;
  private View                bubble;
  private TypingIndicatorView indicator;
  private TextView            typistCount;

  public ConversationTypingView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    avatar1     = findViewById(R.id.typing_avatar_1);
    avatar2     = findViewById(R.id.typing_avatar_2);
    avatar3     = findViewById(R.id.typing_avatar_3);
    badge1      = findViewById(R.id.typing_badge_1);
    badge2      = findViewById(R.id.typing_badge_2);
    badge3      = findViewById(R.id.typing_badge_3);
    typistCount = findViewById(R.id.typing_count);
    bubble      = findViewById(R.id.typing_bubble);
    indicator   = findViewById(R.id.typing_indicator);
  }

  public void setTypists(@NonNull GlideRequests glideRequests, @NonNull List<Recipient> typists, boolean isGroupThread, boolean hasWallpaper) {
    if (typists.isEmpty()) {
      indicator.stopAnimation();
      return;
    }

    avatar1.setVisibility(GONE);
    avatar2.setVisibility(GONE);
    avatar3.setVisibility(GONE);
    badge1.setVisibility(GONE);
    badge2.setVisibility(GONE);
    badge3.setVisibility(GONE);
    typistCount.setVisibility(GONE);

    if (isGroupThread) {
      presentGroupThreadAvatars(glideRequests, typists);
    }

    if (hasWallpaper) {
      bubble.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.conversation_item_wallpaper_bubble_color));
      typistCount.getBackground().setColorFilter(ContextCompat.getColor(getContext(), R.color.conversation_item_wallpaper_bubble_color), PorterDuff.Mode.SRC_IN);
    } else {
      bubble.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.signal_background_secondary));
      typistCount.getBackground().setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_background_secondary), PorterDuff.Mode.SRC_IN);
    }

    indicator.startAnimation();
  }

  private void presentGroupThreadAvatars(@NonNull GlideRequests glideRequests, @NonNull List<Recipient> typists) {
    avatar1.setAvatar(glideRequests, typists.get(0), typists.size() == 1);
    avatar1.setVisibility(VISIBLE);
    badge1.setBadgeFromRecipient(typists.get(0), glideRequests);
    badge1.setVisibility(VISIBLE);

    if (typists.size() > 1) {
      avatar2.setAvatar(glideRequests, typists.get(1), false);
      avatar2.setVisibility(VISIBLE);
      badge2.setBadgeFromRecipient(typists.get(1), glideRequests);
      badge2.setVisibility(VISIBLE);
    }

    if (typists.size() == 3) {
      avatar3.setAvatar(glideRequests, typists.get(2), false);
      avatar3.setVisibility(VISIBLE);
      badge3.setBadgeFromRecipient(typists.get(2), glideRequests);
      badge3.setVisibility(VISIBLE);
    }

    if (typists.size() > 3) {
      typistCount.setText(getResources().getString(R.string.ConversationTypingView__plus_d, typists.size() - 2));
      typistCount.setVisibility(VISIBLE);
    }
  }
}

