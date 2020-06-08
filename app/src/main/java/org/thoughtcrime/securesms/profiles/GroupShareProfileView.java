package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.os.Build;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.ProfileSharingState;
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
        showShareDialog(getContext(), view, recipient);
      }
    });
  }

  public static void showShareDialog(@NonNull Context context,
                                             @NonNull View view,
                                             @NonNull Recipient recipient) {
    new AlertDialog.Builder(context)
            .setIconAttribute(R.attr.dialog_info_icon)
            .setTitle(R.string.GroupShareProfileView_share_your_profile_name_and_photo_with_this_group)
            .setMessage(R.string.GroupShareProfileView_do_you_want_to_make_your_profile_name_and_photo_visible_to_all_current_and_future_members_of_this_group)
            .setPositiveButton(R.string.GroupShareProfileView_make_visible, (dialog, which) -> {
              DatabaseFactory.getRecipientDatabase(context).setProfileSharingState(recipient.getId(), ProfileSharingState.YES);
              Snackbar.make(view, R.string.GroupShareProfileView_profile_now_visible, Snackbar.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.GroupShareProfileView_dont_share, (dialog, which) -> {
              DatabaseFactory.getRecipientDatabase(context).setProfileSharingState(recipient.getId(), ProfileSharingState.NO);
              Snackbar.make(view, R.string.GroupShareProfileView_profile_not_shared, Snackbar.LENGTH_LONG).show();
            }).show();
  }

  public void setRecipient(@NonNull Recipient recipient) {
    this.recipient = recipient;
  }
}
