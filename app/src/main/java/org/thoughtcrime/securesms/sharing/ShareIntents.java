package org.thoughtcrime.securesms.sharing;

import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.stickers.StickerLocator;

import java.util.ArrayList;

final class ShareIntents {

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

    public boolean isEmpty() {
      return extraSticker == null                         &&
             (extraMedia == null || extraMedia.isEmpty()) &&
             TextUtils.isEmpty(extraText);
    }
  }
}
