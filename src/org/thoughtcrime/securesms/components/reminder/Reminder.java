package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.view.View.OnClickListener;

public abstract class Reminder {
  private CharSequence buttonText;
  private CharSequence title;
  private CharSequence text;

  private OnClickListener okListener;
  private OnClickListener dismissListener;

  public Reminder(@NonNull CharSequence title,
                  @NonNull CharSequence text,
                  @NonNull CharSequence buttonText)
  {
    this.title      = title;
    this.text       = text;
    this.buttonText = buttonText;
  }

  public CharSequence getTitle() {
    return title;
  }

  public CharSequence getText() {
    return text;
  }

  public CharSequence getButtonText() {
    return buttonText;
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
}
