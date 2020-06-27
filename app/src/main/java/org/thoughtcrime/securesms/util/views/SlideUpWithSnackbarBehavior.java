package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

public class SlideUpWithSnackbarBehavior extends CoordinatorLayout.Behavior<View> {

  public SlideUpWithSnackbarBehavior(@NonNull Context context, @Nullable AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent,
                                        @NonNull View child,
                                        @NonNull View dependency)
  {
    float translationY = Math.min(0, dependency.getTranslationY() - dependency.getHeight());
    child.setTranslationY(translationY);

    return true;
  }

  @Override
  public void onDependentViewRemoved(@NonNull CoordinatorLayout parent,
                                     @NonNull View child,
                                     @NonNull View dependency)
  {
    child.setTranslationY(0);
  }

  @Override
  public boolean layoutDependsOn(@NonNull CoordinatorLayout parent,
                                 @NonNull View child,
                                 @NonNull View dependency)
  {
    return dependency instanceof Snackbar.SnackbarLayout;
  }
}
