package org.thoughtcrime.securesms.groups.ui.managegroup.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;

/**
 * Dialog to show a full group description. Information regarding the description can be provided
 * as arguments, or a {@link GroupId} can be provided and the dialog will load it. If both are provided,
 * the title/description from the arguments takes precedence.
 */
public final class GroupDescriptionDialog extends DialogFragment {

  private static final String ARGUMENT_GROUP_ID    = "group_id";
  private static final String ARGUMENT_TITLE       = "title";
  private static final String ARGUMENT_DESCRIPTION = "description";
  private static final String ARGUMENT_LINKIFY     = "linkify";
  private static final String DIALOG_TAG           = "GroupDescriptionDialog";

  private EmojiTextView descriptionText;

  public static void show(@NonNull FragmentManager fragmentManager, @NonNull String title, @Nullable String description, boolean linkify) {
    show(fragmentManager, null, title, description, linkify);
  }

  public static void show(@NonNull FragmentManager fragmentManager, @NonNull GroupId groupId, @Nullable String description, boolean linkify) {
    show(fragmentManager, groupId, null, description, linkify);
  }

  private static void show(@NonNull FragmentManager fragmentManager, @Nullable GroupId groupId, @Nullable String title, @Nullable String description, boolean linkify) {
    Bundle arguments = new Bundle();
    arguments.putParcelable(ARGUMENT_GROUP_ID, groupId);
    arguments.putString(ARGUMENT_TITLE, title);
    arguments.putString(ARGUMENT_DESCRIPTION, description);
    arguments.putBoolean(ARGUMENT_LINKIFY, linkify);

    GroupDescriptionDialog dialogFragment = new GroupDescriptionDialog();
    dialogFragment.setArguments(arguments);

    dialogFragment.show(fragmentManager, DIALOG_TAG);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    View      dialogView          = LayoutInflater.from(getContext()).inflate(R.layout.group_description_dialog, null, false);
    String    argumentTitle       = requireArguments().getString(ARGUMENT_TITLE, null);
    String    argumentDescription = requireArguments().getString(ARGUMENT_DESCRIPTION, null);
    GroupId   argumentGroupId     = requireArguments().getParcelable(ARGUMENT_GROUP_ID);
    boolean   linkify             = requireArguments().getBoolean(ARGUMENT_LINKIFY, false);
    LiveGroup liveGroup           = argumentGroupId != null ? new LiveGroup(argumentGroupId) : null;

    descriptionText = dialogView.findViewById(R.id.group_description_dialog_text);
    descriptionText.setMovementMethod(LongClickMovementMethod.getInstance(requireContext()));

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Signal_MaterialAlertDialog);
    Dialog dialog = builder.setTitle(TextUtils.isEmpty(argumentTitle) ? getString(R.string.GroupDescriptionDialog__group_description) : argumentTitle)
                           .setView(dialogView)
                           .setPositiveButton(android.R.string.ok, null)
                           .create();

    if (argumentDescription != null) {
      GroupDescriptionUtil.setText(requireContext(), descriptionText, argumentDescription, linkify, null);
    } else if (liveGroup != null) {
      liveGroup.getDescription().observe(this, d -> GroupDescriptionUtil.setText(requireContext(), descriptionText, d, linkify, null));
    }

    if (TextUtils.isEmpty(argumentTitle) && liveGroup != null) {
      liveGroup.getTitle().observe(this, dialog::setTitle);
    }

    return dialog;
  }
}
