package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public class InsetAwareConstraintLayout extends ConstraintLayout {

  public InsetAwareConstraintLayout(@NonNull Context context) {
    super(context);
  }

  public InsetAwareConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public InsetAwareConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets) {
    if (Build.VERSION.SDK_INT < 30) {
      return super.onApplyWindowInsets(insets);
    }

    Insets windowInsets = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
    applyInsets(new Rect(windowInsets.left, windowInsets.top, windowInsets.right, windowInsets.bottom));

    return super.onApplyWindowInsets(insets);
  }

  @Override
  protected boolean fitSystemWindows(Rect insets) {
    if (Build.VERSION.SDK_INT >= 30) {
      return true;
    }

    applyInsets(insets);

    return true;
  }

  private void applyInsets(@NonNull Rect insets) {
    Guideline statusBarGuideline     = findViewById(R.id.status_bar_guideline);
    Guideline navigationBarGuideline = findViewById(R.id.navigation_bar_guideline);
    Guideline parentStartGuideline   = findViewById(R.id.parent_start_guideline);
    Guideline parentEndGuideline     = findViewById(R.id.parent_end_guideline);

    if (statusBarGuideline != null) {
      statusBarGuideline.setGuidelineBegin(insets.top);
    }

    if (navigationBarGuideline != null) {
      navigationBarGuideline.setGuidelineEnd(insets.bottom);
    }

    if (parentStartGuideline != null) {
      if (ViewUtil.isLtr(this)) {
        parentStartGuideline.setGuidelineBegin(insets.left);
      } else {
        parentStartGuideline.setGuidelineBegin(insets.right);
      }
    }

    if (parentEndGuideline != null) {
      if (ViewUtil.isLtr(this)) {
        parentEndGuideline.setGuidelineEnd(insets.right);
      } else {
        parentEndGuideline.setGuidelineEnd(insets.left);
      }
    }
  }
}
