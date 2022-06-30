package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.DimensionUnit;

public class SlideUpWithSnackbarBehavior extends CoordinatorLayout.Behavior<View> {

  @Dimension(unit = Dimension.DP)
  private static final float PAD_TOP_OF_SNACKBAR_DP = 16f;

  @Px
  private final float padTopOfSnackbar = DimensionUnit.DP.toPixels(PAD_TOP_OF_SNACKBAR_DP);

  public SlideUpWithSnackbarBehavior(@NonNull Context context, @Nullable AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent,
                                        @NonNull View child,
                                        @NonNull View dependency)
  {
    float translationY = Math.min(0, dependency.getTranslationY() - (dependency.getHeight() + padTopOfSnackbar));
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
