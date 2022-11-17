package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.EditTextExtensionsKt;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class EmojiEditText extends AppCompatEditText {

  private final Set<OnFocusChangeListener> onFocusChangeListeners = new HashSet<>();

  public EmojiEditText(Context context) {
    this(context, null);
  }

  public EmojiEditText(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.editTextStyle);
  }

  public EmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
    boolean forceCustom = a.getBoolean(R.styleable.EmojiTextView_emoji_forceCustom, false);
    boolean jumboEmoji = a.getBoolean(R.styleable.EmojiTextView_emoji_forceJumbo, false);
    a.recycle();

    if (!isInEditMode() && (forceCustom || !SignalStore.settings().isPreferSystemEmoji())) {
      setFilters(appendEmojiFilter(this.getFilters(), jumboEmoji));
      setEmojiCompatEnabled(false);
    }

    super.setOnFocusChangeListener((v, hasFocus) -> {
      for (OnFocusChangeListener listener : onFocusChangeListeners) {
        listener.onFocusChange(v, hasFocus);
      }
    });

    if (!isInEditMode()) {
      EditTextExtensionsKt.setIncognitoKeyboardEnabled(this, TextSecurePreferences.isIncognitoKeyboardEnabled(context));
    }
  }

  public void insertEmoji(String emoji) {
    final int          start = getSelectionStart();
    final int          end   = getSelectionEnd();

    getText().replace(Math.min(start, end), Math.max(start, end), emoji);
    setSelection(start + emoji.length());
  }

  @Override
  public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  @Override
  public void setOnFocusChangeListener(@Nullable OnFocusChangeListener listener) {
    if (listener != null) {
      onFocusChangeListeners.add(listener);
    }
  }

  public void addOnFocusChangeListener(@NonNull OnFocusChangeListener listener) {
    onFocusChangeListeners.add(listener);
  }

  private InputFilter[] appendEmojiFilter(@Nullable InputFilter[] originalFilters, boolean jumboEmoji) {
    InputFilter[] result;

    if (originalFilters != null) {
      result = new InputFilter[originalFilters.length + 1];
      System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
    } else {
      result = new InputFilter[1];
    }

    result[0] = new EmojiFilter(this, jumboEmoji);

    return result;
  }

  @Override
  public boolean onTextContextMenuItem(int id) {
    if (id == android.R.id.paste) {
      ClipboardManager clipboardManager = ServiceUtil.getClipboardManager(getContext());
      ClipData         originalClipData = clipboardManager.getPrimaryClip();
      CharSequence     pendingPaste     = getTextFromClipData(originalClipData);

      if (pendingPaste == null) {
        return super.onTextContextMenuItem(id);
      }

      CharSequence sanitizedText = (pendingPaste instanceof Spanned) ? clearFormattingFromText(pendingPaste)
                                                                     : pendingPaste;

      clipboardManager.setPrimaryClip(ClipData.newPlainText("signal_sanitized", sanitizedText));
      boolean performedAction = super.onTextContextMenuItem(id);

      clipboardManager.setPrimaryClip(originalClipData);
      return performedAction;
    }
    return super.onTextContextMenuItem(id);
  }

  private CharSequence clearFormattingFromText(CharSequence text) {
    List<Mention> mentions          = MentionAnnotation.getMentionsFromAnnotations(text);
    Spannable     withoutFormatting = new SpannableString(text.toString());

    MentionAnnotation.setMentionAnnotations(withoutFormatting, mentions);

    return withoutFormatting;
  }

  private @Nullable CharSequence getTextFromClipData(ClipData data) {
    if (data != null && data.getItemCount() > 0) {
      return data.getItemAt(0).coerceToText(getContext());
    } else {
      return null;
    }
  }
}
