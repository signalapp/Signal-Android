package org.thoughtcrime.securesms.giph.ui;


import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.util.ViewUtil;

public class GiphyActivityToolbar extends Toolbar {

  @Nullable private OnFilterChangedListener filterListener;
  @Nullable private OnLayoutChangedListener layoutListener;

  private EditText        searchText;
  private AnimatingToggle toggle;
  private ImageView       action;
  private ImageView       listToggle;
  private ImageView       gridToggle;
  private ImageView       clearToggle;
  private LinearLayout    toggleContainer;

  public GiphyActivityToolbar(Context context) {
    this(context, null);
  }

  public GiphyActivityToolbar(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.toolbarStyle);
  }

  public GiphyActivityToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.giphy_activity_toolbar, this);

    this.action          = ViewUtil.findById(this, R.id.action_icon);
    this.searchText      = ViewUtil.findById(this, R.id.search_view);
    this.toggle          = ViewUtil.findById(this, R.id.button_toggle);
    this.listToggle      = ViewUtil.findById(this, R.id.view_stream);
    this.gridToggle      = ViewUtil.findById(this, R.id.view_grid);
    this.clearToggle     = ViewUtil.findById(this, R.id.search_clear);
    this.toggleContainer = ViewUtil.findById(this, R.id.toggle_container);

    this.listToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        displayTogglingView(gridToggle);
        if (layoutListener != null) layoutListener.onLayoutChanged(OnLayoutChangedListener.LAYOUT_LIST);
      }
    });

    this.gridToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        displayTogglingView(listToggle);
        if (layoutListener != null) layoutListener.onLayoutChanged(OnLayoutChangedListener.LAYOUT_GRID);
      }
    });

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

    expandTapArea(this, action);
    expandTapArea(toggleContainer, gridToggle);
  }

  @Override
  public void setNavigationIcon(int resId) {
    action.setImageResource(resId);
  }

  public void clear() {
    searchText.setText("");
    notifyListener();
  }

  public void setOnLayoutChangedListener(@Nullable OnLayoutChangedListener layoutListener) {
    this.layoutListener = layoutListener;
  }

  public void setOnFilterChangedListener(@Nullable  OnFilterChangedListener filterListener) {
    this.filterListener = filterListener;
  }

  private void notifyListener() {
    if (filterListener != null) filterListener.onFilterChanged(searchText.getText().toString());
  }

  private void displayTogglingView(View view) {
    toggle.display(view);
    expandTapArea(toggleContainer, view);
  }

  private void expandTapArea(final View container, final View child) {
    final int padding = getResources().getDimensionPixelSize(R.dimen.contact_selection_actions_tap_area);

    container.post(new Runnable() {
      @Override
      public void run() {
        Rect rect = new Rect();
        child.getHitRect(rect);

        rect.top -= padding;
        rect.left -= padding;
        rect.right += padding;
        rect.bottom += padding;

        container.setTouchDelegate(new TouchDelegate(rect, child));
      }
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

  public interface OnLayoutChangedListener {
    public static final int LAYOUT_GRID = 1;
    public static final int LAYOUT_LIST = 2;
    void onLayoutChanged(int type);
  }


}
