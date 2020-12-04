package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

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
      getOverflowIcon().setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_icon_tint_primary), PorterDuff.Mode.SRC_ATOP);
    }
  }
}
