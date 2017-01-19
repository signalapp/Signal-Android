package org.thoughtcrime.securesms.components.reminder;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * View to display actionable reminders to the user
 */
public class ReminderView extends LinearLayout {
  private ViewGroup         container;
  private ImageButton       closeButton;
  private TextView          title;
  private TextView          text;
  private OnDismissListener dismissListener;

  public ReminderView(Context context) {
    super(context);
    initialize();
  }

  public ReminderView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @TargetApi(VERSION_CODES.HONEYCOMB)
  public ReminderView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    LayoutInflater.from(getContext()).inflate(R.layout.reminder_header, this, true);
    container   = ViewUtil.findById(this, R.id.container);
    closeButton = ViewUtil.findById(this, R.id.cancel);
    title       = ViewUtil.findById(this, R.id.reminder_title);
    text        = ViewUtil.findById(this, R.id.reminder_text);
  }

  public void showReminder(final Reminder reminder) {
    title.setText(reminder.getTitle());
    text.setText(reminder.getText());

    setOnClickListener(reminder.getOkListener());

    closeButton.setVisibility(reminder.isDismissable() ? View.VISIBLE : View.GONE);
    closeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        hide();
        if (reminder.getDismissListener() != null) reminder.getDismissListener().onClick(v);
        if (dismissListener != null) dismissListener.onDismiss();
      }
    });

    container.setVisibility(View.VISIBLE);
  }

  public void setOnDismissListener(OnDismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public void requestDismiss() {
    closeButton.performClick();
  }

  public void hide() {
    container.setVisibility(View.GONE);
  }

  public interface OnDismissListener {
    void onDismiss();
  }
}
