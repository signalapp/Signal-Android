package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.EditText;

import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.util.ServiceUtil;

public class InputAwareLayout extends KeyboardAwareLinearLayout implements OnKeyboardShownListener {
  private InputView current;

  public InputAwareLayout(Context context) {
    this(context, null);
  }

  public InputAwareLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public InputAwareLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    addOnKeyboardShownListener(this);
  }

  @Override public void onKeyboardShown() {
    hideAttachedInput(true);
  }

  public void show(@NonNull final EditText imeTarget, @NonNull final InputView input) {
    if (isKeyboardOpen()) {
      hideSoftkey(imeTarget, new Runnable() {
        @Override public void run() {
          hideAttachedInput(true);
          input.show(getKeyboardHeight(), true);
          current = input;
        }
      });
    } else {
      if (current != null) current.hide(true);
      input.show(getKeyboardHeight(), current != null);
      current = input;
    }
  }

  public InputView getCurrentInput() {
    return current;
  }

  public void hideCurrentInput(EditText imeTarget) {
    if (isKeyboardOpen()) hideSoftkey(imeTarget, null);
    else                  hideAttachedInput(false);
  }

  public void hideAttachedInput(boolean instant) {
    if (current != null) current.hide(instant);
    current = null;
  }

  public boolean isInputOpen() {
    return (isKeyboardOpen() || (current != null && current.isShowing()));
  }

  public void showSoftkey(final EditText inputTarget) {
    postOnKeyboardOpen(new Runnable() {
      @Override public void run() {
        hideAttachedInput(true);
      }
    });
    inputTarget.post(new Runnable() {
      @Override public void run() {
        inputTarget.requestFocus();
        ServiceUtil.getInputMethodManager(inputTarget.getContext()).showSoftInput(inputTarget, 0);
      }
    });
  }

  private void hideSoftkey(final EditText inputTarget, @Nullable Runnable runAfterClose) {
    if (runAfterClose != null) postOnKeyboardClose(runAfterClose);

    ServiceUtil.getInputMethodManager(inputTarget.getContext())
               .hideSoftInputFromWindow(inputTarget.getWindowToken(), 0);
  }

  public interface InputView {
    void show(int height, boolean immediate);
    void hide(boolean immediate);
    boolean isShowing();
  }
}

