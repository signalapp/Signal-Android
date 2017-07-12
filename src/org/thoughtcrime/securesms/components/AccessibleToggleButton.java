package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.ToggleButton;

public class AccessibleToggleButton extends ToggleButton {

  private OnCheckedChangeListener listener;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public AccessibleToggleButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public AccessibleToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public AccessibleToggleButton(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AccessibleToggleButton(Context context) {
    super(context);
  }

  @Override
  public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
    super.setOnCheckedChangeListener(listener);
    this.listener = listener;
  }

  public void setChecked(boolean checked, boolean notifyListener) {
    if (!notifyListener) {
      super.setOnCheckedChangeListener(null);
    }

    super.setChecked(checked);

    if (!notifyListener) {
      super.setOnCheckedChangeListener(listener);
    }
  }

  public OnCheckedChangeListener getOnCheckedChangeListener() {
    return this.listener;
  }

}
