package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public class InsetAwareConstraintLayout extends ConstraintLayout {

  private WindowInsetsTypeProvider windowInsetsTypeProvider = WindowInsetsTypeProvider.ALL;
  private Insets                   insets;

  public InsetAwareConstraintLayout(@NonNull Context context) {
    super(context);
  }

  public InsetAwareConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public InsetAwareConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setWindowInsetsTypeProvider(@NonNull WindowInsetsTypeProvider windowInsetsTypeProvider) {
    this.windowInsetsTypeProvider = windowInsetsTypeProvider;
    requestLayout();
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets) {
    WindowInsetsCompat windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets);
    Insets             newInsets          = windowInsetsCompat.getInsets(windowInsetsTypeProvider.getInsetsType());

    applyInsets(newInsets);
    return super.onApplyWindowInsets(insets);
  }

  public void applyInsets(@NonNull Insets insets) {
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

  public interface WindowInsetsTypeProvider {

    WindowInsetsTypeProvider ALL = () ->
      WindowInsetsCompat.Type.ime() |
      WindowInsetsCompat.Type.systemBars() |
      WindowInsetsCompat.Type.displayCutout();

    @WindowInsetsCompat.Type.InsetsType
    int getInsetsType();
  }
}
