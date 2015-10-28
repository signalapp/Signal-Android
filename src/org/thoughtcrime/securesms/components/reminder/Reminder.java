package org.thoughtcrime.securesms.components.reminder;

import android.support.annotation.NonNull;
import android.view.View.OnClickListener;

public abstract class Reminder {
  private CharSequence title;
  private CharSequence text;

  private OnClickListener okListener;
  private OnClickListener dismissListener;

  public Reminder(@NonNull CharSequence title,
                  @NonNull CharSequence text)
  {
    this.title      = title;
    this.text       = text;
  }

  public CharSequence getTitle() {
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

  public void setTitle(@NonNull CharSequence title) {
    this.text = title;
  }

  public void setText(@NonNull CharSequence text) {
    this.text = text;
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
}
