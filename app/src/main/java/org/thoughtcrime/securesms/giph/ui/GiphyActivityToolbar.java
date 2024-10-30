package org.thoughtcrime.securesms.giph.ui;


import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import org.thoughtcrime.securesms.R;

public class GiphyActivityToolbar extends Toolbar {

  @Nullable private OnFilterChangedListener filterListener;

  private EditText        searchText;
  private ImageView       action;
  private ImageView       clearToggle;
  private LinearLayout    toggleContainer;

  public GiphyActivityToolbar(Context context) {
    this(context, null);
  }

  public GiphyActivityToolbar(Context context, AttributeSet attrs) {
    this(context, attrs, androidx.appcompat.R.attr.toolbarStyle);
  }

  public GiphyActivityToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.giphy_activity_toolbar, this);

    this.action           = findViewById(R.id.action_icon);
    this.searchText       = findViewById(R.id.search_view);
    this.clearToggle      = findViewById(R.id.search_clear);
    this.toggleContainer  = findViewById(R.id.toggle_container);

    this.clearToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setText("");
        clearToggle.setVisibility(View.INVISIBLE);
      }
    });

    this.searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (SearchUtil.isEmpty(searchText)) clearToggle.setVisibility(View.INVISIBLE);
        else                                clearToggle.setVisibility(View.VISIBLE);

        notifyListener();
      }
    });

    this.searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
          InputMethodManager inputMethodManager = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          inputMethodManager.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
        }

        return false;
      }
    });

    setLogo(null);
    setNavigationIcon(null);
    setContentInsetStartWithNavigation(0);
    expandTapArea(this, action);
  }

  @Override
  public void setNavigationIcon(int resId) {
    action.setImageResource(resId);
  }

  public void clear() {
    searchText.setText("");
    notifyListener();
  }

  public void setOnFilterChangedListener(@Nullable  OnFilterChangedListener filterListener) {
    this.filterListener = filterListener;
  }

  private void notifyListener() {
    if (filterListener != null) filterListener.onFilterChanged(searchText.getText().toString());
  }

  private void expandTapArea(final View container, final View child) {
    final int padding = getResources().getDimensionPixelSize(R.dimen.contact_selection_actions_tap_area);

    container.post(() -> {
      Rect rect = new Rect();
      child.getHitRect(rect);

      rect.top -= padding;
      rect.left -= padding;
      rect.right += padding;
      rect.bottom += padding;

      container.setTouchDelegate(new TouchDelegate(rect, child));
    });
  }

  private static class SearchUtil {
    public static boolean isTextInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    public static boolean isPhoneInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public static boolean isEmpty(EditText editText) {
      return editText.getText().length() <= 0;
    }
  }

  public interface OnFilterChangedListener {
    void onFilterChanged(String filter);
  }
}
