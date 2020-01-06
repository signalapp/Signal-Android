package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;


/**
 * A popup that shows a given sticker fullscreen.
 */
final class StickerPreviewPopup extends PopupWindow {

  private final GlideRequests glideRequests;
  private final ImageView     image;
  private final TextView      emojiText;

  StickerPreviewPopup(@NonNull Context context, @NonNull GlideRequests glideRequests) {
    super(LayoutInflater.from(context).inflate(R.layout.sticker_preview_popup, null),
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT);
    this.glideRequests = glideRequests;
    this.image         = getContentView().findViewById(R.id.sticker_popup_image);
    this.emojiText     = getContentView().findViewById(R.id.sticker_popup_emoji);

    setTouchable(false);
  }

  void presentSticker(@NonNull Object stickerGlideModel, @Nullable String emoji) {
    emojiText.setText(emoji);
    glideRequests.load(stickerGlideModel)
                 .into(image);
  }
}
