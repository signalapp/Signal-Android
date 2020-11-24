package org.thoughtcrime.securesms.imageeditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.imageeditor.renderers.TextRenderer;

/**
 * Invisible {@link android.widget.EditText} that is used during in-image text editing.
 */
final class HiddenEditText extends androidx.appcompat.widget.AppCompatEditText {

  @SuppressLint("InlinedApi")
  private static final int INCOGNITO_KEYBOARD_IME = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;

  @Nullable
  private TextRenderer currentTextEntity;

  @Nullable
  private Runnable onEndEdit;

  public HiddenEditText(Context context) {
    super(context);
    setAlpha(0);
    setLayoutParams(new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));
    setClickable(false);
    setFocusable(true);
    setFocusableInTouchMode(true);
    setBackgroundColor(Color.TRANSPARENT);
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 1);
    setInputType(InputType.TYPE_CLASS_TEXT);
    setImeOptions(EditorInfo.IME_ACTION_DONE);
    clearFocus();
  }

  @Override
  protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
    super.onTextChanged(text, start, lengthBefore, lengthAfter);
    if (currentTextEntity != null) {
      currentTextEntity.setText(text.toString());
    }
  }

  @Override
  public void onEditorAction(int actionCode) {
    super.onEditorAction(actionCode);
    if (actionCode == EditorInfo.IME_ACTION_DONE && currentTextEntity != null) {
      currentTextEntity.setFocused(false);
      endEdit();
    }
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    if (currentTextEntity != null) {
      currentTextEntity.setFocused(focused);
      if (!focused) {
        endEdit();
      }
    }
  }

  private void endEdit() {
    if (onEndEdit != null) {
      onEndEdit.run();
    }
  }

  @Nullable TextRenderer getCurrentTextEntity() {
    return currentTextEntity;
  }

  void setCurrentTextEntity(@Nullable TextRenderer currentTextEntity) {
    if (this.currentTextEntity != currentTextEntity) {
      if (this.currentTextEntity != null) {
        this.currentTextEntity.setFocused(false);
      }
      this.currentTextEntity = currentTextEntity;
      if (currentTextEntity != null) {
        String text = currentTextEntity.getText();
        setText(text);
        setSelection(text.length());
      } else {
        setText("");
      }
    }
  }

  @Override
  protected void onSelectionChanged(int selStart, int selEnd) {
    super.onSelectionChanged(selStart, selEnd);
    if (currentTextEntity != null) {
      currentTextEntity.setSelection(selStart, selEnd);
    }
  }

  @Override
  public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
    boolean focus = super.requestFocus(direction, previouslyFocusedRect);

    if (currentTextEntity != null && focus) {
      currentTextEntity.setFocused(true);
      InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
      if (!imm.isAcceptingText()) {
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
      }
    }

    return focus;
  }

  public void hideKeyboard() {
    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
  }

  public void setIncognitoKeyboardEnabled(boolean incognitoKeyboardEnabled) {
    setImeOptions(incognitoKeyboardEnabled ? getImeOptions() |  INCOGNITO_KEYBOARD_IME
                                           : getImeOptions() & ~INCOGNITO_KEYBOARD_IME);
  }

  public void setOnEndEdit(@Nullable Runnable onEndEdit) {
    this.onEndEdit = onEndEdit;
  }
}
