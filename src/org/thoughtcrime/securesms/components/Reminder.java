package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;

public abstract class Reminder {
  private Integer         iconResId;
  private Integer         titleResId;
  private Integer         textResId;
  private OnClickListener okListener;
  private OnClickListener cancelListener;

  public Reminder(Integer iconResId, Integer titleResId, Integer textResId) {
    this.iconResId  = iconResId;
    this.titleResId = titleResId;
    this.textResId  = textResId;
  }

  public Integer getIconResId() {
    return iconResId;
  }

  public Integer getTitleResId() {
    return titleResId;
  }

  public Integer getTextResId() {
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

  public String getOKText() {
    return null;
  }

  public Integer getCancelResId() { return R.drawable.ic_menu_remove_holo_light; }
}
