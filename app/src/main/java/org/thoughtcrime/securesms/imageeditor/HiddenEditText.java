package org.thoughtcrime.securesms.imageeditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.renderers.MultiLineTextRenderer;

/**
 * Invisible {@link android.widget.EditText} that is used during in-image text editing.
 */
final class HiddenEditText extends androidx.appcompat.widget.AppCompatEditText {

  @SuppressLint("InlinedApi")
  private static final int INCOGNITO_KEYBOARD_IME = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;

  @Nullable
  private EditorElement currentTextEditorElement;

  @Nullable
  private MultiLineTextRenderer currentTextEntity;

  @Nullable
  private Runnable onEndEdit;

  @Nullable
  private OnEditOrSelectionChange onEditOrSelectionChange;

  public HiddenEditText(Context context) {
    super(context);
    setAlpha(0);
    setLayoutParams(new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));
    setClickable(false);
    setFocusable(true);
    setFocusableInTouchMode(true);
    setBackgroundColor(Color.TRANSPARENT);
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 1);
    setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    clearFocus();
  }

  @Override
  protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
    super.onTextChanged(text, start, lengthBefore, lengthAfter);
    if (currentTextEntity != null) {
      currentTextEntity.setText(text.toString());
      postEditOrSelectionChange();
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

  private void postEditOrSelectionChange() {
    if (currentTextEditorElement != null && currentTextEntity != null && onEditOrSelectionChange != null) {
      onEditOrSelectionChange.onChange(currentTextEditorElement, currentTextEntity);
    }
  }

  @Nullable MultiLineTextRenderer getCurrentTextEntity() {
    return currentTextEntity;
  }

  @Nullable EditorElement getCurrentTextEditorElement() {
    return currentTextEditorElement;
  }

  public void setCurrentTextEditorElement(@Nullable EditorElement currentTextEditorElement) {
    if (currentTextEditorElement != null && currentTextEditorElement.getRenderer() instanceof MultiLineTextRenderer) {
      this.currentTextEditorElement = currentTextEditorElement;
      setCurrentTextEntity((MultiLineTextRenderer) currentTextEditorElement.getRenderer());
    } else {
      this.currentTextEditorElement = null;
      setCurrentTextEntity(null);
    }

    postEditOrSelectionChange();
  }

  private void setCurrentTextEntity(@Nullable MultiLineTextRenderer currentTextEntity) {
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
      postEditOrSelectionChange();
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

  public void setOnEditOrSelectionChange(@Nullable OnEditOrSelectionChange onEditOrSelectionChange) {
    this.onEditOrSelectionChange = onEditOrSelectionChange;
  }

  public interface OnEditOrSelectionChange {
    void onChange(@NonNull EditorElement editorElement, @NonNull MultiLineTextRenderer textRenderer);
  }
}
