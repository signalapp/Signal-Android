package org.thoughtcrime.securesms.components;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.HashSet;
import java.util.Set;

public class InputManager implements OnKeyboardShownListener {
  private final KeyboardAwareLinearLayout container;
  private final TextView                  inputTarget;

  private InputView current;

  public InputManager(@NonNull KeyboardAwareLinearLayout container,
                      @NonNull TextView inputTarget)
  {
    this.container   = container;
    this.inputTarget = inputTarget;
    container.addOnKeyboardShownListener(this);
  }

  @Override public void onKeyboardShown() {
    hideAttachedInput();
  }

  public void show(@NonNull final InputView input) {
    if (container.isKeyboardOpen()) {
      hideSoftkey(new Runnable() {
        @Override public void run() {
          input.show(container.getKeyboardHeight(), true);
        }
      });
    } else if (current != null && current.isShowing()) {
      current.hide(true);
      input.show(container.getKeyboardHeight(), true);
    } else {
      input.show(container.getKeyboardHeight(), false);
    }

    current = input;
  }

  public InputView getCurrentInput() {
    return current;
  }

  public void hideCurrentInput() {
    if (container.isKeyboardOpen()) hideSoftkey(null);
    else                            hideAttachedInput();
  }

  public void hideAttachedInput() {
    if (current != null) current.hide(true);
    current = null;
  }

  public boolean isInputOpen() {
    return (container.isKeyboardOpen() || (current != null && current.isShowing()));
  }

  public void showSoftkey() {
    container.postOnKeyboardOpen(new Runnable() {
      @Override public void run() {
        hideAttachedInput();
      }
    });
    inputTarget.post(new Runnable() {
      @Override public void run() {
        inputTarget.requestFocus();
        ServiceUtil.getInputMethodManager(inputTarget.getContext()).showSoftInput(inputTarget, 0);
      }
    });
  }

  private void hideSoftkey(@Nullable Runnable runAfterClose) {
    if (runAfterClose != null) container.postOnKeyboardClose(runAfterClose);

    ServiceUtil.getInputMethodManager(inputTarget.getContext())
               .hideSoftInputFromWindow(inputTarget.getWindowToken(), 0);
  }

  public interface InputView {
    void show(int height, boolean immediate);
    void hide(boolean immediate);
    boolean isShowing();
  }
}

