package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.view.View.OnClickListener;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.LinkedList;
import java.util.List;

public abstract class Reminder {
  public static final int NO_RESOURCE = -1;

  private int title;
  private int text;

  private OnClickListener okListener;
  private OnClickListener dismissListener;

  private final List<Action> actions = new LinkedList<>();

  /**
   * For a reminder that wishes to generate it's own strings by overwriting
   * {@link #getText(Context)} and {@link #getTitle(Context)}
   */
  public Reminder() {
    this(NO_RESOURCE, NO_RESOURCE);
  }

  public Reminder(@StringRes int textRes) {
    this(NO_RESOURCE, textRes);
  }

  public Reminder(@StringRes int titleRes, @StringRes int textRes) {
    this.title = titleRes;
    this.text  = textRes;
  }

  public @Nullable CharSequence getTitle(@NonNull Context context) {
    if (title == NO_RESOURCE) {
      return null;
    }

    return context.getString(title);
  }

  public @NonNull CharSequence getText(@NonNull Context context) {
    Preconditions.checkArgument(text != NO_RESOURCE);
    return context.getString(text);
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

  protected void addAction(@NonNull Action action) {
    actions.add(action);
  }

  public List<Action> getActions() {
    return actions;
  }

  public int getProgress() {
    return -1;
  }

  public enum Importance {
    NORMAL, ERROR, TERMINAL
  }

  public static class Action {
    private final int title;
    private final int actionId;

    public Action(@IdRes int actionId) {
      this(NO_RESOURCE, actionId);
    }

    public Action(@StringRes int title, @IdRes int actionId) {
      this.title    = title;
      this.actionId = actionId;
    }

    public CharSequence getTitle(@NonNull Context context) {
      Preconditions.checkArgument(title != NO_RESOURCE);
      return context.getText(title);
    }

    public int getActionId() {
      return actionId;
    }
  }
}
