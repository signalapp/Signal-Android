package org.thoughtcrime.securesms.keyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;


@SuppressWarnings("unused")
public final class TopShadowBehavior extends CoordinatorLayout.Behavior<View> {

  private int     targetId;
  private boolean shown = true;

  public TopShadowBehavior(int targetId) {
    super();
    this.targetId = targetId;
  }

  public TopShadowBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TopShadowBehavior);
      targetId = a.getResourceId(R.styleable.TopShadowBehavior_app_bar_layout_id, 0);
      a.recycle();
    }

    if (targetId == 0) {
      throw new IllegalStateException();
    }
  }

  @Override
  public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    return dependency instanceof RecyclerView;
  }

  @Override
  public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    boolean shouldShow = dependency.getY() != parent.findViewById(targetId).getHeight();

    if (shouldShow != shown) {
      if (shouldShow) {
        show(child);
      } else {
        hide(child);
      }
      shown = shouldShow;
    }

    if (child.getY() != 0) {
      child.setY(0);
      return true;
    }

    return false;
  }

  private void show(View child) {
    child.animate()
         .setDuration(250)
         .alpha(1f);
  }

  private void hide(View child) {
    child.animate()
         .setDuration(250)
         .alpha(0f);
  }
}
