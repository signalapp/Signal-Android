package org.thoughtcrime.securesms.components.reminder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View.OnClickListener;

public abstract class Reminder {
  private CharSequence title;
  private CharSequence text;

  private OnClickListener okListener;
  private OnClickListener dismissListener;

  public Reminder(@Nullable CharSequence title,
                  @NonNull  CharSequence text)
  {
    this.title = title;
    this.text  = text;
  }

  public @Nullable CharSequence getTitle() {
    return title;
  }

  public CharSequence getText() {
    return text;
  }

  public OnClickListener getOkListener() {
    return okListener;
  }

  public OnClickListener getDismissListener() {
    return dismissListener;
  }

  public void setOkListener(OnClickListener okListener) {
    this.okListener = okListener;
  }

  public void setDismissListener(OnClickListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public boolean isDismissable() {
    return true;
  }

  public @NonNull Importance getImportance() {
    return Importance.NORMAL;
  }


  public enum Importance {
    NORMAL, ERROR
  }
}
