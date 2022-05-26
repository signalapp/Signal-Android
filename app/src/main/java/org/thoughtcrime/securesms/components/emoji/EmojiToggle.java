package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyboard.KeyboardPage;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class EmojiToggle extends AppCompatImageButton implements MediaKeyboard.MediaKeyboardListener {

  private Drawable emojiToggle;
  private Drawable stickerToggle;
  private Drawable gifToggle;

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
    this.emojiToggle   = ContextUtil.requireDrawable(getContext(), R.drawable.ic_emoji);
    this.stickerToggle = ContextUtil.requireDrawable(getContext(), R.drawable.ic_sticker_24);
    this.gifToggle     = ContextUtil.requireDrawable(getContext(), R.drawable.ic_gif_24);
    this.imeToggle     = ContextUtil.requireDrawable(getContext(), R.drawable.ic_keyboard_24);
    this.mediaToggle   = emojiToggle;

    setToMedia();
  }

  public void attach(MediaKeyboard drawer) {
    drawer.setKeyboardListener(this);
  }

  public void setStickerMode(@NonNull KeyboardPage page) {
    switch (page) {
      case EMOJI:
        mediaToggle = emojiToggle;
        break;
      case STICKER:
        mediaToggle = stickerToggle;
        break;
      case GIF:
        mediaToggle = gifToggle;
        break;
    }

    if (getDrawable() != imeToggle) {
      setToMedia();
    }
  }

  public boolean isStickerMode() {
    return this.mediaToggle == stickerToggle;
  }

  @Override public void onShown() {
    setToIme();
  }

  @Override public void onHidden() {
    setToMedia();
  }

  @Override
  public void onKeyboardChanged(@NonNull KeyboardPage page) {
    setStickerMode(page);
    switch (page) {
      case EMOJI:
        TextSecurePreferences.setMediaKeyboardMode(getContext(), TextSecurePreferences.MediaKeyboardMode.EMOJI);
        break;
      case STICKER:
        TextSecurePreferences.setMediaKeyboardMode(getContext(), TextSecurePreferences.MediaKeyboardMode.STICKER);
        break;
      case GIF:
        TextSecurePreferences.setMediaKeyboardMode(getContext(), TextSecurePreferences.MediaKeyboardMode.GIF);
        break;
    }
  }
}
