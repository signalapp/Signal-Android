package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.AppCompatTextView;

import org.thoughtcrime.securesms.R;

public final class WarningTextView extends AppCompatTextView {

  @ColorInt private final int originalTextColor;
  @ColorInt private final int warningTextColor;

  private boolean warning;

  public WarningTextView(Context context) {
    this(context, null);
  }

  public WarningTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WarningTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.WarningTextView, 0, 0);
    warningTextColor = styledAttributes.getColor(R.styleable.WarningTextView_warning_text_color, 0);

    styledAttributes.recycle();

    styledAttributes = context.obtainStyledAttributes(attrs, new int[]{ android.R.attr.textColor });

    originalTextColor = styledAttributes.getColor(0, 0);

    styledAttributes.recycle();
  }

  public void setWarning(boolean warning) {
    if (this.warning != warning) {
      this.warning = warning;
      setTextColor(warning ? warningTextColor : originalTextColor);
      invalidate();
    }
  }
}
