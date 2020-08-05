package org.thoughtcrime.securesms.groups.ui.managegroup.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.RecipientDatabase.MentionSetting;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

public final class GroupMentionSettingDialog {

  public static void show(@NonNull Context context, @NonNull MentionSetting mentionSetting, @Nullable Consumer<MentionSetting> callback) {
    SelectionCallback selectionCallback = new SelectionCallback(mentionSetting, callback);

    new AlertDialog.Builder(context)
                   .setTitle(R.string.GroupMentionSettingDialog_notify_me_for_mentions)
                   .setView(getView(context, mentionSetting, selectionCallback))
                   .setPositiveButton(android.R.string.ok, selectionCallback)
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
  }

  @SuppressLint("InflateParams")
  private static View getView(@NonNull Context context, @NonNull MentionSetting mentionSetting, @NonNull SelectionCallback selectionCallback) {
    View            root          = LayoutInflater.from(context).inflate(R.layout.group_mention_setting_dialog, null, false);
    CheckedTextView defaultOption = root.findViewById(R.id.group_mention_setting_default);
    CheckedTextView alwaysNotify  = root.findViewById(R.id.group_mention_setting_always_notify);
    CheckedTextView dontNotify    = root.findViewById(R.id.group_mention_setting_dont_notify);

    defaultOption.setText(SignalStore.notificationSettings().isMentionNotifiesMeEnabled() ? R.string.GroupMentionSettingDialog_default_notify_me
                                                                                          : R.string.GroupMentionSettingDialog_default_dont_notify_me);

    View.OnClickListener listener = (v) -> {
      defaultOption.setChecked(defaultOption == v);
      alwaysNotify.setChecked(alwaysNotify == v);
      dontNotify.setChecked(dontNotify == v);

      if      (defaultOption.isChecked()) selectionCallback.selection = MentionSetting.GLOBAL;
      else if (alwaysNotify.isChecked())  selectionCallback.selection = MentionSetting.ALWAYS_NOTIFY;
      else if (dontNotify.isChecked())    selectionCallback.selection = MentionSetting.DO_NOT_NOTIFY;
    };

    defaultOption.setOnClickListener(listener);
    alwaysNotify.setOnClickListener(listener);
    dontNotify.setOnClickListener(listener);

    switch (mentionSetting) {
      case GLOBAL:
        listener.onClick(defaultOption);
        break;
      case ALWAYS_NOTIFY:
        listener.onClick(alwaysNotify);
        break;
      case DO_NOT_NOTIFY:
        listener.onClick(dontNotify);
        break;
    }

    return root;
  }

  private static class SelectionCallback implements DialogInterface.OnClickListener {

    @NonNull  private final MentionSetting           previousMentionSetting;
    @NonNull  private       MentionSetting           selection;
    @Nullable private final Consumer<MentionSetting> callback;

    public SelectionCallback(@NonNull MentionSetting previousMentionSetting, @Nullable Consumer<MentionSetting> callback) {
      this.previousMentionSetting = previousMentionSetting;
      this.selection              = previousMentionSetting;
      this.callback               = callback;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null && selection != previousMentionSetting) {
        callback.accept(selection);
      }
    }
  }
}
