package org.thoughtcrime.securesms.components.emoji;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class EmojiEditText extends AppCompatEditText {
  private static final String TAG = EmojiEditText.class.getSimpleName();

  public EmojiEditText(Context context) {
    this(context, null);
  }

  public EmojiEditText(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.editTextStyle);
  }

  public EmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    if (!TextSecurePreferences.isSystemEmojiPreferred(getContext())) {
      setFilters(appendEmojiFilter(this.getFilters()));
    }
  }

  public void insertEmoji(String emoji) {
    final int          start = getSelectionStart();
    final int          end   = getSelectionEnd();

    getText().replace(Math.min(start, end), Math.max(start, end), emoji);
    setSelection(start + emoji.length());
  }

  /*
  Paste events are watched here so that rich text is never inserted into an EmojiEditText
   */
  @Override
  public boolean onTextContextMenuItem(int id) {
    // we only care about the paste option
    if (id != android.R.id.paste) return super.onTextContextMenuItem(id);
    // the paste option was selected
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // the system handles plain text pasting perfectly fine on M and above
      return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);
    } else { // manual fallback for pre-M versions of Android
      ClipboardManager cm = ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE));
      ClipData primaryClip = cm.getPrimaryClip();
      if (primaryClip == null || primaryClip.getItemCount() == 0)
        return super.onTextContextMenuItem(id); // if we don't have anything to paste, leave
      CharSequence clip = primaryClip.getItemAt(0).coerceToText(getContext());
      if (clip == null) return super.onTextContextMenuItem(id); // nothing to paste
      // remove the formatting of the clipped text
      CharSequence sanitized = (clip instanceof Spanned) ? clip.toString() : clip;
      ClipData cd = ClipData.newPlainText("signal_sanitized", sanitized);
      cm.setPrimaryClip(cd);
      boolean retVal = super.onTextContextMenuItem(id); // apply the sanitized paste
      // restore the ClipboardManager to its original state
      cm.setPrimaryClip(primaryClip);
      return retVal;
    }
  }

  @Override
  public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  private InputFilter[] appendEmojiFilter(@Nullable InputFilter[] originalFilters) {
    InputFilter[] result;

    if (originalFilters != null) {
      result = new InputFilter[originalFilters.length + 1];
      System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
    } else {
      result = new InputFilter[1];
    }

    result[0] = new EmojiFilter(this);

    return result;
  }
}
