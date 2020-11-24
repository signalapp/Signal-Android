package org.thoughtcrime.securesms.components;

import android.content.Context;
import com.google.android.material.tabs.TabLayout;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/**
 * An implementation of {@link TabLayout} that disables taps when the view is disabled.
 */
public class ControllableTabLayout extends TabLayout {

  private List<View> touchables;

  public ControllableTabLayout(Context context) {
    super(context);
  }

  public ControllableTabLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ControllableTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (isEnabled() && !enabled) {
      touchables = getTouchables();
    }

    for (View touchable : touchables) {
      touchable.setClickable(enabled);
    }

    super.setEnabled(enabled);
  }
}
