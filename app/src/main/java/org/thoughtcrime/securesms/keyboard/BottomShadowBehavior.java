package org.thoughtcrime.securesms.keyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import org.thoughtcrime.securesms.R;


@SuppressWarnings("unused")
public final class BottomShadowBehavior extends CoordinatorLayout.Behavior<View> {

  private int     bottomBarId;
  private boolean shown = true;

  public BottomShadowBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomShadowBehavior);
      bottomBarId = a.getResourceId(R.styleable.BottomShadowBehavior_bottom_bar_id, 0);
      a.recycle();
    }

    if (bottomBarId == 0) {
      throw new IllegalStateException();
    }
  }

  @Override
  public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    return dependency.getId() == bottomBarId;
  }

  @Override
  public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
    float alpha = (dependency.getHeight() - (int) dependency.getTranslationY()) / (float) dependency.getHeight();
    child.setAlpha(alpha);

    float y = dependency.getY() - child.getHeight();
    if (y != child.getY()) {
      child.setY(y);
      return true;
    }

    return false;
  }
}
