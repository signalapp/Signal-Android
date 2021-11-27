package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * LinearLayout that supports being checkable, useful for complicated "selectedable"
 * buttons that aren't really buttons.
 */
public final class CheckedLinearLayout extends LinearLayout implements Checkable {
  private static final int[]   CHECKED_STATE = { android.R.attr.state_checked };
  private              boolean checked       = false;

  public CheckedLinearLayout(Context context) {
    super(context);
  }

  public CheckedLinearLayout(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CheckedLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected @NonNull Parcelable onSaveInstanceState() {
    return new InstanceState(Objects.requireNonNull(super.onSaveInstanceState()), checked);
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    InstanceState instanceState = (InstanceState) state;
    super.onRestoreInstanceState(instanceState.getSuperState());
    setChecked(instanceState.checked);
  }

  @Override
  public void setChecked(boolean checked) {
    if (this.checked != checked) {
      toggle();
    }
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void toggle() {
    checked = !checked;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child instanceof Checkable) {
        ((Checkable) child).setChecked(checked);
      }
    }
    refreshDrawableState();
  }

  @Override
  protected int[] onCreateDrawableState(final int extraSpace) {
    final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (isChecked()) {
      mergeDrawableStates(drawableState, CHECKED_STATE);
    }
    return drawableState;
  }

  private static class InstanceState extends BaseSavedState {
    private final boolean checked;

    InstanceState(@NonNull Parcelable superState, boolean checked) {
      super(superState);
      this.checked = checked;
    }

    private InstanceState(@NonNull Parcel in) {
      super(in);
      checked = in.readInt() > 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeInt(checked ? 1 : 0);
    }

    public static final Parcelable.Creator<InstanceState> CREATOR = new Parcelable.Creator<InstanceState>() {
      public InstanceState createFromParcel(Parcel in) {
        return new InstanceState(in);
      }

      public InstanceState[] newArray(int size) {
        return new InstanceState[size];
      }
    };
  }
}
