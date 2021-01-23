package org.thoughtcrime.securesms.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public class LongClickCopySpan extends URLSpan {
  private static final String PREFIX_MAILTO = "mailto:";
  private static final String PREFIX_TEL = "tel:";

  private boolean isHighlighted;
  @ColorInt
  private int highlightColor;

  public LongClickCopySpan(String url) {
    super(url);
  }

  void onLongClick(View widget) {
    Context context = widget.getContext();
    String preparedUrl = prepareUrl(getURL());
    copyUrl(context, preparedUrl);
    Toast.makeText(context,
            context.getString(R.string.ConversationItem_copied_text, preparedUrl), Toast.LENGTH_SHORT).show();
  }

  @Override
  public void updateDrawState(@NonNull TextPaint ds) {
    super.updateDrawState(ds);
    ds.bgColor = highlightColor;
    ds.setUnderlineText(!isHighlighted);
  }

  void setHighlighted(boolean highlighted, @ColorInt int highlightColor) {
    this.isHighlighted = highlighted;
    this.highlightColor = highlightColor;
  }

  private void copyUrl(Context context, String url) {
    ClipboardManager clipboard = ContextCompat.getSystemService(context, ClipboardManager.class);
    ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), url);
    clipboard.setPrimaryClip(clip);
  }

  private String prepareUrl(String url) {
    if (url.startsWith(PREFIX_MAILTO)) {
      return url.substring(PREFIX_MAILTO.length());
    } else if (url.startsWith(PREFIX_TEL)) {
      return url.substring(PREFIX_TEL.length());
    }
    return url;
  }
}
