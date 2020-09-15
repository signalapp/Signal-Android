package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.Objects;

public class StickerSlide extends Slide {

  public static final int WIDTH  = 512;
  public static final int HEIGHT = 512;

  private final StickerLocator stickerLocator;

  public StickerSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
    this.stickerLocator = Objects.requireNonNull(attachment.getSticker());
  }

  public StickerSlide(Context context, Uri uri, long size, @NonNull StickerLocator stickerLocator, @NonNull String contentType) {
    super(context, constructAttachmentFromUri(context, uri, contentType, size, WIDTH, HEIGHT, true, null, null, stickerLocator, null, null, false, false, false));
    this.stickerLocator = Objects.requireNonNull(attachment.getSticker());
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
  public @NonNull String getContentDescription() {
    return context.getString(R.string.Slide_sticker);
  }

  public @Nullable String getEmoji() {
    return stickerLocator.getEmoji();
  }
}
