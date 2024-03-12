package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.stickers.StickerLocator;

import java.util.Objects;

public class StickerSlide extends Slide {

  public static final int WIDTH  = 512;
  public static final int HEIGHT = 512;

  private final StickerLocator stickerLocator;

  public StickerSlide(@NonNull Attachment attachment) {
    super(attachment);
    this.stickerLocator = Objects.requireNonNull(attachment.stickerLocator);
  }

  public StickerSlide(Context context, Uri uri, long size, @NonNull StickerLocator stickerLocator, @NonNull String contentType) {
    super(constructAttachmentFromUri(context, uri, contentType, size, WIDTH, HEIGHT, true, null, null, stickerLocator, null, null, false, false, false, false));
    this.stickerLocator = Objects.requireNonNull(attachment.stickerLocator);
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return 0;
  }

  @Override
  public boolean hasSticker() {
    return true;
  }

  @Override
  public boolean isBorderless() {
    return true;
  }

  @Override
  public @NonNull String getContentDescription(Context context) {
    return context.getString(R.string.Slide_sticker);
  }

  public @Nullable String getEmoji() {
    return stickerLocator.emoji;
  }
}
