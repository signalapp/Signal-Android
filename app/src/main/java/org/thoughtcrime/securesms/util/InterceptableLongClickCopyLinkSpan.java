package org.thoughtcrime.securesms.util;

import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Passes clicked Urls to the supplied {@link UrlClickHandler}.
 */
public final class InterceptableLongClickCopyLinkSpan extends LongClickCopySpan {

  private final UrlClickHandler onClickListener;

  public InterceptableLongClickCopyLinkSpan(@NonNull String url,
                                            @NonNull UrlClickHandler onClickListener)
  {
    this(url, onClickListener, null, true);
  }

  public InterceptableLongClickCopyLinkSpan(@NonNull String url,
                                            @NonNull UrlClickHandler onClickListener,
                                            @ColorInt Integer textColor,
                                            boolean underline)
  {
    super(url, textColor, underline);
    this.onClickListener = onClickListener;
  }

  @Override
  public void onClick(View widget) {
    if (!onClickListener.handleOnClick(getURL())) {
      super.onClick(widget);
    }
  }
}
