package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.signal.core.util.ServiceUtil;

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

  @Override
  public void onKeyboardShown() {
  }

  @Override
  protected boolean onOfferNavigationBar(int inset) {
    return current != null && current.isShowing() && current.claimNavigationBar(inset);
  }

  public void show(@NonNull final EditText imeTarget, @NonNull final InputView input) {
    if (isKeyboardOpen()) {
      hideSoftkey(imeTarget, new Runnable() {
        @Override public void run() {
          hideAttachedInput(true);
          input.show(getKeyboardHeight(), true);
          current = input;
          ViewCompat.requestApplyInsets(InputAwareLayout.this);
        }
      });
    } else {
      if (current != null) current.hide(true);
      input.show(getKeyboardHeight(), current != null);
      current = input;
      ViewCompat.requestApplyInsets(this);
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
    ViewCompat.requestApplyInsets(this);
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

  public void hideSoftkey(final EditText inputTarget, @Nullable Runnable runAfterClose) {
    if (runAfterClose != null) postOnKeyboardClose(runAfterClose);

    ServiceUtil.getInputMethodManager(inputTarget.getContext())
               .hideSoftInputFromWindow(inputTarget.getWindowToken(), 0);
  }

  public interface InputView {
    void show(int height, boolean immediate);
    void hide(boolean immediate);
    boolean isShowing();

    /**
     * Offers the navigation bar beneath this view while it shows. One that takes it grows by {@code inset} to paint behind the
     * bar, keeps its own contents clear of it, and returns true.
     */
    default boolean claimNavigationBar(int inset) {
      return false;
    }
  }
}

