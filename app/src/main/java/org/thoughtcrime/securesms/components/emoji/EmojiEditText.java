package org.thoughtcrime.securesms.components.emoji;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.EditTextExtensionsKt;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
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

    TypedArray a           = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
    boolean    forceCustom = a.getBoolean(R.styleable.EmojiTextView_emoji_forceCustom, false);
    boolean    jumboEmoji  = a.getBoolean(R.styleable.EmojiTextView_emoji_forceJumbo, false);
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
    final int start = getSelectionStart();
    final int end   = getSelectionEnd();

    getText().replace(Math.min(start, end), Math.max(start, end), emoji);
    setSelection(start + emoji.length());
  }

  @Override
  public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) {
      invalidate();
    } else {
      super.invalidateDrawable(drawable);
    }
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
      ClipData clipData = ServiceUtil.getClipboardManager(getContext()).getPrimaryClip();

      if (clipData != null) {
        CharSequence label        = clipData.getDescription().getLabel();
        CharSequence pendingPaste = getTextFromClipData(clipData);

        if (TextUtils.equals(Util.COPY_LABEL, label) && shouldPersistSignalStylingWhenPasting()) {
          return super.onTextContextMenuItem(id);
        } else if (Build.VERSION.SDK_INT >= 23) {
          return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);
        } else if (pendingPaste != null) {
          Util.copyToClipboard(getContext(), pendingPaste.toString());
          return super.onTextContextMenuItem(id);
        }
      }
    } else if (id == android.R.id.copy || id == android.R.id.cut) {
      boolean          originalResult   = super.onTextContextMenuItem(id);
      ClipboardManager clipboardManager = ServiceUtil.getClipboardManager(getContext());
      CharSequence     clipText         = getTextFromClipData(clipboardManager.getPrimaryClip());

      if (clipText != null) {
        Util.copyToClipboard(getContext(), clipText);
        return true;
      }

      return originalResult;
    }

    return super.onTextContextMenuItem(id);
  }

  private @Nullable CharSequence getTextFromClipData(@Nullable ClipData data) {
    if (data != null && data.getItemCount() > 0) {
      return data.getItemAt(0).coerceToText(getContext());
    } else {
      return null;
    }
  }

  protected boolean shouldPersistSignalStylingWhenPasting() {
    return false;
  }
}
