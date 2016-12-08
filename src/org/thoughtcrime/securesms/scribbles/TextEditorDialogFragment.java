/**
 * Copyright (c) 2016 UPTech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.thoughtcrime.securesms.scribbles;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import org.thoughtcrime.securesms.R;

/**
 * Transparent Dialog Fragment, with no title and no background
 * <p>
 * The fragment imitates capturing input from keyboard, but does not display anything
 * the result from input from the keyboard is passed through {@link TextEditorDialogFragment.OnTextLayerCallback}
 * <p>
 * Activity that uses {@link TextEditorDialogFragment} must implement {@link TextEditorDialogFragment.OnTextLayerCallback}
 * <p>
 * If Activity does not implement {@link TextEditorDialogFragment.OnTextLayerCallback}, exception will be thrown at Runtime
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class TextEditorDialogFragment extends DialogFragment {

  public static final String ARG_TEXT = "editor_text_arg";

  protected EditText editText;

  private OnTextLayerCallback callback;

  /**
   * deprecated
   * use {@link TextEditorDialogFragment#getInstance(String)}
   */
  @Deprecated
  public TextEditorDialogFragment() {
    // empty, use getInstance
  }

  public static TextEditorDialogFragment getInstance(String textValue) {
    @SuppressWarnings("deprecation")
    TextEditorDialogFragment fragment = new TextEditorDialogFragment();
    Bundle args = new Bundle();
    args.putString(ARG_TEXT, textValue);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof OnTextLayerCallback) {
      this.callback = (OnTextLayerCallback) activity;
    } else {
      throw new IllegalStateException(activity.getClass().getName()
                                          + " must implement " + OnTextLayerCallback.class.getName());
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.scribble_text_editor_layout, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    Bundle args = getArguments();
    String text = "";
    if (args != null) {
      text = args.getString(ARG_TEXT);
    }

    editText = (EditText) view.findViewById(R.id.edit_text_view);

    initWithTextEntity(text);

    editText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (callback != null) {
          callback.textChanged(s.toString());
        }
      }
    });

    view.findViewById(R.id.text_editor_root).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // exit when clicking on background
        dismiss();
      }
    });
  }

  private void initWithTextEntity(String text) {
    editText.setText(text);
    editText.post(new Runnable() {
      @Override
      public void run() {
        if (editText != null) {
          Selection.setSelection(editText.getText(), editText.length());
        }
      }
    });
  }

  @Override
  public void dismiss() {
    super.dismiss();

    // clearing memory on exit, cos manipulating with text uses bitmaps extensively
    // this does not frees memory immediately, but still can help
    System.gc();
    Runtime.getRuntime().gc();
  }

  @Override
  public void onDetach() {
    // release links
    this.callback = null;
    super.onDetach();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.requestWindowFeature(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    return dialog;
  }

  @Override
  public void onStart() {
    super.onStart();
    Dialog dialog = getDialog();
    if (dialog != null) {
      Window window = dialog.getWindow();
      if (window != null) {
        // remove background
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        // remove dim
        WindowManager.LayoutParams windowParams = window.getAttributes();
        window.setDimAmount(0.0F);
        window.setAttributes(windowParams);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    editText.post(new Runnable() {
      @Override
      public void run() {
        // force show the keyboard
        setEditText(true);
        editText.requestFocus();
        InputMethodManager ims = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        ims.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
      }
    });
  }

  private void setEditText(boolean gainFocus) {
    if (!gainFocus) {
      editText.clearFocus();
      editText.clearComposingText();
    }
    editText.setFocusableInTouchMode(gainFocus);
    editText.setFocusable(gainFocus);
  }

  /**
   * Callback that passes all user input through the method
   * {@link TextEditorDialogFragment.OnTextLayerCallback#textChanged(String)}
   */
  public interface OnTextLayerCallback {
    void textChanged(@NonNull String text);
  }
}