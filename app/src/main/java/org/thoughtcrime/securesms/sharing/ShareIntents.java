package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.stickers.StickerLocator;

import java.util.ArrayList;
import java.util.Collection;

public final class ShareIntents {

  private static final String EXTRA_MEDIA      = "extra_media";
  private static final String EXTRA_BORDERLESS = "extra_borderless";
  private static final String EXTRA_STICKER    = "extra_sticker";

  private ShareIntents() {
  }

  public static final class Args {

    private final CharSequence     extraText;
    private final ArrayList<Media> extraMedia;
    private final StickerLocator   extraSticker;
    private final boolean          isBorderless;

    public static Args from(@NonNull Intent intent) {
      return new Args(intent.getStringExtra(Intent.EXTRA_TEXT),
                      intent.getParcelableArrayListExtra(EXTRA_MEDIA),
                      intent.getParcelableExtra(EXTRA_STICKER),
                      intent.getBooleanExtra(EXTRA_BORDERLESS, false));
    }

    private Args(@Nullable CharSequence extraText,
                 @Nullable ArrayList<Media> extraMedia,
                 @Nullable StickerLocator extraSticker,
                 boolean isBorderless)
    {
      this.extraText    = extraText;
      this.extraMedia   = extraMedia;
      this.extraSticker = extraSticker;
      this.isBorderless = isBorderless;
    }

    public @Nullable ArrayList<Media> getExtraMedia() {
      return extraMedia;
    }

    public @Nullable CharSequence getExtraText() {
      return extraText;
    }

    public @Nullable StickerLocator getExtraSticker() {
      return extraSticker;
    }

    public boolean isBorderless() {
      return isBorderless;
    }
  }

  public static final class Builder {

    private final Context context;

    private String           extraText;
    private ArrayList<Media> extraMedia;
    private Slide            slide;

    public Builder(@NonNull Context context) {
      this.context = context;
    }

    public @NonNull Builder setText(@NonNull CharSequence extraText) {
      this.extraText = extraText.toString();
      return this;
    }

    public @NonNull Builder setMedia(@NonNull Collection<Media> extraMedia) {
      this.extraMedia = new ArrayList<>(extraMedia);
      return this;
    }

    public @NonNull Builder setSlide(@NonNull Slide slide) {
      this.slide = slide;
      return this;
    }

    public @NonNull Intent build() {
      if (slide != null && extraMedia != null) {
        throw new IllegalStateException("Cannot create intent with both Slide and [Media]");
      }

      Intent intent = new Intent(context, ShareActivity.class);

      intent.putExtra(Intent.EXTRA_TEXT, extraText);

      if (extraMedia != null) {
        intent.putParcelableArrayListExtra(EXTRA_MEDIA, extraMedia);
      } else if (slide != null) {
        intent.putExtra(Intent.EXTRA_STREAM, slide.getUri());
        intent.putExtra(EXTRA_BORDERLESS, slide.isBorderless());

        if (slide.hasSticker()) {
          intent.putExtra(EXTRA_STICKER, slide.asAttachment().getSticker());
          intent.setType(slide.asAttachment().getContentType());
        } else {
          intent.setType(slide.getContentType());
        }
      }

      return intent;
    }
  }
}
