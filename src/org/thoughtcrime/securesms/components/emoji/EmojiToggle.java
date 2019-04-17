package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class EmojiToggle extends AppCompatImageButton implements MediaKeyboard.MediaKeyboardListener {

  private Drawable emojiToggle;
  private Drawable stickerToggle;

  private Drawable mediaToggle;
  private Drawable imeToggle;


  public EmojiToggle(Context context) {
    super(context);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setToMedia() {
    setImageDrawable(mediaToggle);
  }

  public void setToIme() {
    setImageDrawable(imeToggle);
  }

  private void initialize() {
    int attributes[] = new int[] {R.attr.conversation_emoji_toggle,
                                  R.attr.conversation_sticker_toggle,
                                  R.attr.conversation_keyboard_toggle};

    TypedArray drawables = getContext().obtainStyledAttributes(attributes);
    this.emojiToggle     = drawables.getDrawable(0);
    this.stickerToggle   = drawables.getDrawable(1);
    this.imeToggle       = drawables.getDrawable(2);
    this.mediaToggle     = emojiToggle;

    drawables.recycle();
    setToMedia();
  }

  public void attach(MediaKeyboard drawer) {
    drawer.setKeyboardListener(this);
  }

  public void setStickerMode(boolean stickerMode) {
    this.mediaToggle = stickerMode ? stickerToggle : emojiToggle;

    if (getDrawable() != imeToggle) {
      setToMedia();
    }
  }

  @Override public void onShown() {
    setToIme();
  }

  @Override public void onHidden() {
    setToMedia();
  }

  @Override
  public void onKeyboardProviderChanged(@NonNull MediaKeyboardProvider provider) {
    setStickerMode(provider instanceof StickerKeyboardProvider);
    TextSecurePreferences.setMediaKeyboardMode(getContext(), (provider instanceof StickerKeyboardProvider) ? TextSecurePreferences.MediaKeyboardMode.STICKER
                                                                                                           : TextSecurePreferences.MediaKeyboardMode.EMOJI);
  }
}
