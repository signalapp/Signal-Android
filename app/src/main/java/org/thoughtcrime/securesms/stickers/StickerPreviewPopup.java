package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;


/**
 * A popup that shows a given sticker fullscreen.
 */
final class StickerPreviewPopup extends PopupWindow {

  private final RequestManager requestManager;
  private final ImageView      image;
  private final TextView       emojiText;

  StickerPreviewPopup(@NonNull Context context, @NonNull RequestManager requestManager) {
    super(LayoutInflater.from(context).inflate(R.layout.sticker_preview_popup, null),
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT);
    this.requestManager = requestManager;
    this.image          = getContentView().findViewById(R.id.sticker_popup_image);
    this.emojiText      = getContentView().findViewById(R.id.sticker_popup_emoji);

    setTouchable(false);
  }

  void presentSticker(@NonNull Object stickerGlideModel, @Nullable String emoji) {
    emojiText.setText(emoji);
    requestManager.load(stickerGlideModel)
                 .diskCacheStrategy(DiskCacheStrategy.NONE)
                 .fitCenter()
                 .into(image);
  }
}
