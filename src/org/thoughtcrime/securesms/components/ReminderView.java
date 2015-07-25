package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

/**
 * View to display actionable reminders to the user
 */
public class ReminderView extends LinearLayout {
  private ViewGroup   container;
  private ImageButton cancel;
  private TextView    title;
  private TextView    text;
  private ImageView   icon;

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
    container = (ViewGroup  ) findViewById(R.id.container);
    cancel    = (ImageButton) findViewById(R.id.cancel);
    title     = (TextView   ) findViewById(R.id.reminder_title);
    text      = (TextView   ) findViewById(R.id.reminder_text);
    icon      = (ImageView  ) findViewById(R.id.icon);
  }

  public void showReminder(final Reminder reminder) {
    icon.setImageResource(reminder.getIconResId());
    title.setText(reminder.getTitleResId());
    text.setText(reminder.getTextResId());

    this.setOnClickListener(reminder.getOkListener());

    if (reminder.isDismissable()) {
      cancel.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          hide();
          if (reminder.getCancelListener() != null) reminder.getCancelListener().onClick(v);
        }
      });
    } else {
      cancel.setVisibility(View.GONE);
    }

    container.setVisibility(View.VISIBLE);
  }

  public void hide() {
    container.setVisibility(View.GONE);
  }
}
