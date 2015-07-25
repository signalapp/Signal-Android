package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;

public abstract class Reminder {
  private int             iconResId;
  private int             titleResId;
  private int             textResId;
  private OnClickListener okListener;
  private OnClickListener cancelListener;

  public Reminder(int iconResId, int titleResId, int textResId) {
    this.iconResId  = iconResId;
    this.titleResId = titleResId;
    this.textResId  = textResId;
  }

  public int getIconResId() {
    return iconResId;
  }

  public int getTitleResId() {
    return titleResId;
  }

  public int getTextResId() {
    return textResId;
  }

  public OnClickListener getOkListener() {
    return okListener;
  }

  public OnClickListener getCancelListener() {
    return cancelListener;
  }

  public void setOkListener(OnClickListener okListener) {
    this.okListener = okListener;
  }

  public void setCancelListener(OnClickListener cancelListener) {
    this.cancelListener = cancelListener;
  }

  public boolean isDismissable() {
    return true;
  }
}
