package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * An implementation of {@link TabLayout} that disables taps when the view is disabled.
 */
public class ControllableTabLayout extends TabLayout {

  private List<View> touchables;

  private NewTabListener newTabListener;

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

  public void setNewTabListener(@Nullable NewTabListener newTabListener) {
    this.newTabListener = newTabListener;
  }

  @Override
  public @NonNull Tab newTab() {
    Tab tab = super.newTab();

    if (newTabListener != null) {
      newTabListener.onNewTab(tab);
    }

    return tab;
  }

  /**
   * Allows implementor to modify tabs when they are created, before they are added to the tab layout.
   * This is useful for loading custom views, to ensure that time is not spent inflating these views
   * as the user is switching between pages.
   */
  public interface NewTabListener {
    void onNewTab(@NonNull Tab tab);
  }
}
