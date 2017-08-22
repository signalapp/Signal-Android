package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StyleRes;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ViewUtil;

public class GroupShareProfileView extends FrameLayout {

  private           View      container;
  private @Nullable Recipient recipient;

  public GroupShareProfileView(@NonNull Context context) {
    super(context);
    initialize();
  }

  public GroupShareProfileView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public GroupShareProfileView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public GroupShareProfileView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.profile_group_share_view, this);

    this.container = ViewUtil.findById(this, R.id.container);
    this.container.setOnClickListener(view -> {
      if (this.recipient != null) {
        new AlertDialog.Builder(getContext())
            .setIconAttribute(R.attr.dialog_info_icon)
            .setTitle(R.string.GroupShareProfileView_share_your_profile_name_and_photo_with_this_group)
            .setMessage(R.string.GroupShareProfileView_do_you_want_to_make_your_profile_name_and_photo_visible_to_all_current_and_future_members_of_this_group)
            .setPositiveButton(R.string.GroupShareProfileView_make_visible, (dialog, which) -> {
              DatabaseFactory.getRecipientDatabase(getContext()).setProfileSharing(recipient, true);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
      }
    });
  }

  public void setRecipient(@NonNull Recipient recipient) {
    this.recipient = recipient;
  }
}
