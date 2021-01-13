package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

public class LabeledEditText extends FrameLayout implements View.OnFocusChangeListener {

  private TextView  label;
  private EditText  input;
  private View      border;
  private ViewGroup textContainer;

  public LabeledEditText(@NonNull Context context) {
    super(context);
    init(null);
  }

  public LabeledEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.labeled_edit_text, this);

    String labelText       = "";
    int    backgroundColor = Color.BLACK;
    int    textLayout      = R.layout.labeled_edit_text_default;

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.LabeledEditText, 0, 0);

      labelText       = typedArray.getString(R.styleable.LabeledEditText_labeledEditText_label);
      backgroundColor = typedArray.getColor(R.styleable.LabeledEditText_labeledEditText_background, Color.BLACK);
      textLayout      = typedArray.getResourceId(R.styleable.LabeledEditText_labeledEditText_textLayout, R.layout.labeled_edit_text_default);

      typedArray.recycle();
    }

    label         = findViewById(R.id.label);
    border        = findViewById(R.id.border);
    textContainer = findViewById(R.id.text_container);

    inflate(getContext(), textLayout, textContainer);
    input = findViewById(R.id.input);

    label.setText(labelText);
    label.setBackgroundColor(backgroundColor);

    if (TextUtils.isEmpty(labelText)) {
      label.setVisibility(INVISIBLE);
    }

    input.setOnFocusChangeListener(this);
  }

  public EditText getInput() {
    return input;
  }

  public void setText(String text) {
    input.setText(text);
  }

  public Editable getText() {
    return input.getText();
  }

  @Override
  public void onFocusChange(View v, boolean hasFocus) {
    border.setBackgroundResource(hasFocus ? R.drawable.labeled_edit_text_background_active
                                          : R.drawable.labeled_edit_text_background_inactive);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    input.setEnabled(enabled);
  }

  public void focusAndMoveCursorToEndAndOpenKeyboard() {
    input.requestFocus();

    int numberLength = getText().length();
    input.setSelection(numberLength, numberLength);

    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);

    if (!imm.isAcceptingText()) {
      imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }
  }
}
