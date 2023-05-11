package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.SimpleColorFilter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public final class ConversationScrollToView extends FrameLayout {

  private final TextView  unreadCount;
  private final ImageView scrollButton;
  private final Animation inAnimation;
  private final Animation outAnimation;

  public ConversationScrollToView(@NonNull Context context) {
    this(context, null);
  }

  public ConversationScrollToView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ConversationScrollToView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.conversation_scroll_to, this);

    unreadCount  = findViewById(R.id.conversation_scroll_to_count);
    scrollButton = findViewById(R.id.conversation_scroll_to_button);

    if (attrs != null && !isInEditMode()) {
      TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ConversationScrollToView);
      int        srcId = array.getResourceId(R.styleable.ConversationScrollToView_cstv_scroll_button_src, 0);

      scrollButton.setImageResource(srcId);

      array.recycle();
    }

    inAnimation  = AnimationUtils.loadAnimation(context, R.anim.fade_scale_in);
    outAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_scale_out);

    inAnimation.setDuration(100);
    outAnimation.setDuration(50);
  }

  public void setShown(boolean isShown) {
    if (isShown) {
      ViewUtil.animateIn(this, inAnimation);
    } else {
      ViewUtil.animateOut(this, outAnimation, View.INVISIBLE);
    }
  }

  public void setWallpaperEnabled(boolean hasWallpaper) {
    if (hasWallpaper) {
      scrollButton.setBackgroundResource(R.drawable.scroll_to_bottom_background_wallpaper);
    } else {
      scrollButton.setBackgroundResource(R.drawable.scroll_to_bottom_background_normal);
    }
  }

  public void setUnreadCountBackgroundTint(@ColorInt int tint) {
    unreadCount.getBackground().setColorFilter(new SimpleColorFilter(tint));
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener l) {
    scrollButton.setOnClickListener(l);
  }

  public void setUnreadCount(int unreadCount) {
    this.unreadCount.setText(formatUnreadCount(unreadCount));
    this.unreadCount.setVisibility(unreadCount > 0 ? VISIBLE : GONE);
  }

  private @NonNull CharSequence formatUnreadCount(int unreadCount) {
    return unreadCount > 999 ? "999+" : String.valueOf(unreadCount);
  }
}
