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

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.recipients.Recipient;

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

  public void setTypists(@NonNull RequestManager requestManager, @NonNull List<Recipient> typists, boolean isGroupThread, boolean hasWallpaper) {
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
      presentGroupThreadAvatars(requestManager, typists);
    }

    if (hasWallpaper) {
      bubble.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.conversation_item_recv_bubble_color_wallpaper));
      typistCount.getBackground().setColorFilter(ContextCompat.getColor(getContext(), R.color.conversation_item_recv_bubble_color_wallpaper), PorterDuff.Mode.SRC_IN);
      indicator.setDotTint(ContextCompat.getColor(getContext(), R.color.conversation_typing_indicator_foreground_tint_wallpaper));
    } else {
      bubble.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.conversation_item_recv_bubble_color_normal));
      typistCount.getBackground().setColorFilter(ContextCompat.getColor(getContext(), R.color.conversation_item_recv_bubble_color_normal), PorterDuff.Mode.SRC_IN);
      indicator.setDotTint(ContextCompat.getColor(getContext(), R.color.conversation_typing_indicator_foreground_tint_normal));
    }

    indicator.startAnimation();
  }

  public boolean isActive() {
    return indicator.isActive();
  }

  private void presentGroupThreadAvatars(@NonNull RequestManager requestManager, @NonNull List<Recipient> typists) {
    avatar1.setAvatar(requestManager, typists.get(0), typists.size() == 1);
    avatar1.setVisibility(VISIBLE);
    badge1.setBadgeFromRecipient(typists.get(0), requestManager);
    badge1.setVisibility(VISIBLE);

    if (typists.size() > 1) {
      avatar2.setAvatar(requestManager, typists.get(1), false);
      avatar2.setVisibility(VISIBLE);
      badge2.setBadgeFromRecipient(typists.get(1), requestManager);
      badge2.setVisibility(VISIBLE);
    }

    if (typists.size() == 3) {
      avatar3.setAvatar(requestManager, typists.get(2), false);
      avatar3.setVisibility(VISIBLE);
      badge3.setBadgeFromRecipient(typists.get(2), requestManager);
      badge3.setVisibility(VISIBLE);
    }

    if (typists.size() > 3) {
      typistCount.setText(getResources().getString(R.string.ConversationTypingView__plus_d, typists.size() - 2));
      typistCount.setVisibility(VISIBLE);
    }
  }
}

