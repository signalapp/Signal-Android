package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;

import org.thoughtcrime.securesms.R;

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

  public InsetAwareConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected boolean fitSystemWindows(Rect insets) {
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
      parentStartGuideline.setGuidelineBegin(insets.left);
    }

    if (parentEndGuideline != null) {
      parentEndGuideline.setGuidelineEnd(insets.right);
    }

    return true;
  }
}
