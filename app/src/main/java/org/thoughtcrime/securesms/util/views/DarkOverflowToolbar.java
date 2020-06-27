package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;

/**
 * It seems to be impossible to tint the overflow icon in an ActionMode independently from the
 * default toolbar overflow icon. So we default the overflow icon to white, then we can use this
 * subclass to make it the correct themed color for most use cases.
 */
public class DarkOverflowToolbar extends Toolbar {
  public DarkOverflowToolbar(Context context) {
    super(context);
    init();
  }

  public DarkOverflowToolbar(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public DarkOverflowToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    if (getOverflowIcon() != null) {
      getOverflowIcon().setColorFilter(ThemeUtil.getThemedColor(getContext(), R.attr.icon_tint), PorterDuff.Mode.SRC_ATOP);
    }
  }
}
