package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.UnreadPayments;

/**
 * Displays the data in a given UnreadPayments object in a banner.
 */
public class UnreadPaymentsView extends ConstraintLayout {

  private TextView        title;
  private AvatarImageView avatar;
  private Listener        listener;

  public UnreadPaymentsView(@NonNull Context context) {
    super(context);
  }

  public UnreadPaymentsView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public UnreadPaymentsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public UnreadPaymentsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    title  = findViewById(R.id.payment_notification_title);
    avatar = findViewById(R.id.payment_notification_avatar);

    View open  = findViewById(R.id.payment_notification_touch_target);
    View close = findViewById(R.id.payment_notification_close_touch_target);

    open.setOnClickListener(v -> {
      if (listener != null) listener.onOpenPaymentsNotificationClicked();
    });

    close.setOnClickListener(v -> {
      if (listener != null) listener.onClosePaymentsNotificationClicked();
    });
  }

  public void setListener(@NonNull Listener listener) {
    this.listener = listener;
  }

  public void setUnreadPayments(@NonNull UnreadPayments unreadPayments) {
    title.setText(unreadPayments.getDescription(getContext()));
    avatar.setAvatar(unreadPayments.getRecipient());
    avatar.setVisibility(unreadPayments.getRecipient() == null ? GONE : VISIBLE);
  }

  public interface Listener {
    void onOpenPaymentsNotificationClicked();

    void onClosePaymentsNotificationClicked();
  }
}
