package org.thoughtcrime.securesms;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, Recipients> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipients recipients;
  private final Context    context;

  private ProgressDialog progress = null;

  public GroupMembersDialog(Context context, Recipients recipients) {
    this.recipients = recipients;
    this.context    = context;
  }

  @Override
  public void onPreExecute() {
    progress = ProgressDialog.show(context, context.getString(R.string.GroupMembersDialog_members), context.getString(R.string.GroupMembersDialog_members), true, false);
  }

  @Override
  protected Recipients doInBackground(Void... params) {
    try {
      String groupId = recipients.getPrimaryRecipient().getNumber();
      return DatabaseFactory.getGroupDatabase(context)
                            .getGroupMembers(GroupUtil.getDecodedId(groupId), true);
    } catch (IOException e) {
      Log.w("ConverstionActivity", e);
      return new Recipients(new LinkedList<Recipient>());
    }
  }

  @Override
  public void onPostExecute(Recipients members) {
    if (progress != null) {
      progress.dismiss();
    }

    List<String> recipientStrings = new LinkedList<>();
    recipientStrings.add(context.getString(R.string.GroupMembersDialog_me));

    for (Recipient recipient : members.getRecipientsList()) {
      if (!isLocalNumber(recipient)) {
        recipientStrings.add(recipient.toShortString());
      }
    }

    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
    builder.setItems(recipientStrings.toArray(new String[]{}), null);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    if (recipients.isGroupRecipient()) execute();
    else                               onPostExecute(recipients);
  }

  private boolean isLocalNumber(Recipient recipient) {
    try {
      String localNumber = TextSecurePreferences.getLocalNumber(context);
      String e164Number  = Util.canonicalizeNumber(context, recipient.getNumber());

      return e164Number != null && e164Number.equals(localNumber);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}
